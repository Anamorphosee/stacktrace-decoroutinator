@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.generator.internal

import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorTransformed
import dev.reformator.stacktracedecoroutinator.runtime.internal.BASE_CONTINUATION_CLASS_NAME
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import java.io.InputStream
import java.lang.invoke.MethodHandles
import kotlin.coroutines.Continuation

data class DebugMetadataInfo internal constructor(
    internal val internalClassName: String,
    internal val methodName: String,
    internal val fileName: String?,
    internal val lineNumbers: Set<Int>
)

fun tryTransformForDecoroutinator(
    className: String,
    classBody: InputStream,
    metadataResolver: (className: String) -> DebugMetadataInfo?
): ByteArray? {
    if (className.startsWith("java.")) {
        return null
    }
    if (className == BASE_CONTINUATION_CLASS_NAME) {
        return loadDecoroutinatorBaseContinuationClassBody()
    }
    val node = getClassNode(classBody) ?: return null
    if (Type.getObjectType(node.name).className != className || node.isTransformed) {
        return null
    }
    val metadata = node.getMetadataInfo(metadataResolver) ?: return null
    node.transform(metadata)
    return node.classBody
}

fun getDebugMetadataInfoFromClassBody(body: InputStream): DebugMetadataInfo? =
    getClassNode(body, skipCode = true)?.debugMetadataInfo

fun getDebugMetadataInfoFromClass(clazz: Class<*>): DebugMetadataInfo? =
    JavaUtils.getDebugMetadataInfo(clazz)

private val debugMetadataAnnotationClassDescriptor = Type.getDescriptor(JavaUtils.metadataAnnotationClass)

private data class MetadataInfo(
    val fileName: String?,
    val suspendFuncName2LineNumbers: Map<String, Set<Int>>
)

private fun ClassNode.transform(metadataInfo: MetadataInfo) {
    version = maxOf(version, Opcodes.V1_8)
    metadataInfo.suspendFuncName2LineNumbers.forEach { (methodName, lineNumbers) ->
        methods.add(buildSpecMethodNode(methodName, lineNumbers, true))
    }
    val clinit = getOrCreateClinitMethod()
    clinit.instructions.insertBefore(
        clinit.instructions.first,
        buildCallRegisterLookupInstructions()
    )
    if (visibleAnnotations == null) {
        visibleAnnotations = mutableListOf()
    }
    visibleAnnotations.add(metadataInfo.transformedAnnotation)
}

private fun getClassNode(classBody: InputStream, skipCode: Boolean = false): ClassNode? {
    return try {
        val classReader = ClassReader(classBody)
        val classNode = ClassNode(Opcodes.ASM9)
        classReader.accept(classNode, if (skipCode) ClassReader.SKIP_CODE else 0)
        classNode
    } catch (_: Exception) {
        null
    }
}

private val ClassNode.isTransformed: Boolean
    get() = visibleAnnotations
        .orEmpty()
        .find { it.desc == Type.getDescriptor(DecoroutinatorTransformed::class.java) } != null

private fun ClassNode.getMetadataInfo(metadataResolver: (className: String) -> DebugMetadataInfo?): MetadataInfo? {
    val suspendFunc2LineNumbers = mutableMapOf<String, MutableSet<Int>>()
    val fileNames = mutableSetOf<String>()
    val check = { info: DebugMetadataInfo? ->
        if (info != null && info.internalClassName == name && info.lineNumbers.isNotEmpty()) {
            val currentLineNumbers = suspendFunc2LineNumbers.computeIfAbsent(info.methodName) {
                mutableSetOf()
            }
            currentLineNumbers.addAll(info.lineNumbers)
            if (info.fileName != null) {
                fileNames.add(info.fileName)
            }
        }
    }
    check(debugMetadataInfo)
    methods.orEmpty().forEach { method ->
        if (method.hasCode && method.isSuspend) {
            check(method.getDebugMetadataInfo(
                classInternalName = name,
                metadataResolver = metadataResolver
            ))
        }
    }
    return if (suspendFunc2LineNumbers.isNotEmpty()) {
        val fileName = fileNames.run { when {
            isEmpty() -> null
            size == 1 -> single()
            else -> throw IllegalStateException(
                "class [$name] contains suspend fun metadata with multiple file names: [$this]"
            )
        } }
        MetadataInfo(fileName, suspendFunc2LineNumbers)
    } else {
        null
    }
}

@Suppress("UNCHECKED_CAST")
private val ClassNode.debugMetadataInfo: DebugMetadataInfo?
    get() {
        visibleAnnotations.orEmpty().forEach { annotation ->
            if (annotation.desc == debugMetadataAnnotationClassDescriptor) {
                val parameters = annotation.values
                    .chunked(2) { it[0] as String to it[1] as Any }
                    .toMap()
                val internalClassName = (parameters["c"] as String).replace('.', '/')
                val methodName = parameters["m"] as String
                val fileName = (parameters["f"] as String).ifEmpty { null }
                val lineNumbers = (parameters["l"] as List<Int>).toSet()
                if (lineNumbers.isEmpty()) {
                    return null
                }
                return DebugMetadataInfo(
                    internalClassName = internalClassName,
                    methodName = methodName,
                    fileName = fileName,
                    lineNumbers = lineNumbers,
                )
            }
        }
        return null
    }

private val MethodNode.hasCode: Boolean
    get() = instructions != null && instructions.size() > 0

private val MethodNode.isSuspend: Boolean
    get() = desc.endsWith("${Type.getDescriptor(Continuation::class.java)})${Type.getDescriptor(Object::class.java)}")

private fun MethodNode.getDebugMetadataInfo(
    classInternalName: String,
    metadataResolver: (className: String) -> DebugMetadataInfo?
): DebugMetadataInfo? {
    val continuationIndex = run {
        var continuationIndex = 0
        if (!isStatic) {
            continuationIndex++
        }
        val arguments = Type.getArgumentTypes(desc)
        arguments.asSequence()
            .take(arguments.size - 1)
            .forEach { continuationIndex += it.size }
        continuationIndex
    }
    return instructions.asSequence()
        .mapNotNull {
            val next = it.next
            if (it is VarInsnNode && it.opcode == Opcodes.ALOAD && it.`var` == continuationIndex &&
                next != null && next is TypeInsnNode && next.opcode == Opcodes.INSTANCEOF) {
                next
            } else {
                null
            }
        }
        .firstOrNull()?.let {
            val continuationClassInternalName: String = it.desc
            if (continuationClassInternalName != classInternalName) {
                val continuationClassName = Type.getObjectType(it.desc).className
                metadataResolver(continuationClassName)
            } else {
                null
            }
        }
}

private val MethodNode.isStatic: Boolean
    get() = access and Opcodes.ACC_STATIC != 0

private fun ClassNode.getOrCreateClinitMethod(): MethodNode =
    methods.firstOrNull {
        it.name == "<clinit>" && it.desc == "()V" && it.isStatic
    } ?: MethodNode(Opcodes.ASM9).apply {
        name = "<clinit>"
        desc = "()V"
        access = Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC
        instructions.add(InsnNode(Opcodes.RETURN))
        methods.add(this)
    }

private fun buildCallRegisterLookupInstructions() = InsnList().apply {
    add(MethodInsnNode(
        Opcodes.INVOKESTATIC,
        Type.getInternalName(MethodHandles::class.java),
        "lookup",
        "()${Type.getDescriptor(MethodHandles.Lookup::class.java)}"
    ))
    add(MethodInsnNode(
        Opcodes.INVOKESTATIC,
        Type.getInternalName(registerTransformedFunctionClass),
        registerTransformedFunctionName,
        "(${Type.getDescriptor(MethodHandles.Lookup::class.java)})V"
    ))
}

private val MetadataInfo.transformedAnnotation: AnnotationNode
    get() {
        val result = AnnotationNode(Opcodes.ASM9, Type.getDescriptor(DecoroutinatorTransformed::class.java))
        val list = suspendFuncName2LineNumbers.entries.toList()
        result.values = buildList {
            if (fileName != null) {
                add("fileName")
                add(fileName)
            } else {
                add("fileNamePresent")
                add(false)
            }
            add("methodNames")
            add(list.map { it.key })
            add("lineNumbersCounts")
            add(list.map { it.value.size })
            add("lineNumbers")
            add(list.flatMap { it.value })
        }
        return result
    }

private val ClassNode.classBody: ByteArray
    get() {
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        accept(writer)
        return writer.toByteArray()
    }
