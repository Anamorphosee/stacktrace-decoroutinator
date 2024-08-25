@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.generator.internal

import dev.reformator.stacktracedecoroutinator.common.internal.*
import dev.reformator.stacktracedecoroutinator.generator.intrinsics.DebugMetadata
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.*
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import java.io.InputStream
import java.lang.invoke.MethodHandles
import kotlin.coroutines.Continuation

class TransformationStatus(
    updatedBody: ByteArray?,
    needReadProviderModule: Boolean
)

fun tryTransformForDecoroutinator(
    classBody: InputStream,
    metadataResolver: (className: String) -> DebugMetadataInfo?
): TransformationStatus {
    val node = getClassNode(classBody) ?: return noTransformationStatus
    val version = node.decoroutinatorTransformedVersion
    if (version != null) {
        if (version == TRANSFORMED_VERSION) {
            return readProviderTransformationStatus
        }
        if (version > TRANSFORMED_VERSION) {
            error("class [${node.name}]'s transformed meta has version [$version]. Please update Decoroutinator")
        }
    }
    if (node.name == BASE_CONTINUATION_CLASS_NAME.internalName) {
        transformBaseContinuation(node)
        return TransformationStatus(
            updatedBody = node.classBody,
            needReadProviderModule = true
        )
    }
    val transformationInfo = node.getClassTransformationInfo(metadataResolver) ?: return noTransformationStatus
    node.transform(transformationInfo)
    return TransformationStatus(
        updatedBody = node.classBody,
        needReadProviderModule = true
    )
}

fun transformBaseContinuation(baseContinuation: ClassNode) {
    val resumeWithMethod = baseContinuation.methods?.find {
        it.name == BaseContinuation::resumeWith.name && it.desc == "(${Type.getDescriptor(Object::class.java)})V"
                && !it.isStatic
    } ?: error("[${BaseContinuation::resumeWith.name}] method is not found")

    resumeWithMethod.instructions.insertBefore(resumeWithMethod.instructions.first, InsnList().apply {
        add(MethodInsnNode(
            Opcodes.INVOKESTATIC,
            Type.getInternalName(providerApiClass),
            ::isDecoroutinatorEnabled.name,
            "()${Type.BOOLEAN_TYPE.descriptor}"
        ))
        val defaultAwakeLabel = LabelNode()
        add(JumpInsnNode(
            Opcodes.IFEQ,
            defaultAwakeLabel
        ))
        add(MethodInsnNode(
            Opcodes.INVOKESTATIC,
            Type.getInternalName(providerApiClass),
            ::isBaseContinuationPrepared.name,
            "()${Type.BOOLEAN_TYPE.descriptor}"
        ))
        val decoroutinatorAwakeLabel = LabelNode()
        add(JumpInsnNode(
            Opcodes.IFNE,
            decoroutinatorAwakeLabel
        ))
        add(MethodInsnNode(
            Opcodes.INVOKESTATIC,
            Type.getInternalName(MethodHandles::class.java),
            MethodHandles::lookup.name,
            "()${Type.getDescriptor(MethodHandles.Lookup::class.java)}"
        ))
        add(MethodInsnNode(
            Opcodes.INVOKESTATIC,
            Type.getInternalName(providerApiClass),
            ::prepareBaseContinuation.name,
            "(${Type.getDescriptor(MethodHandles.Lookup::class.java)})${Type.VOID_TYPE.descriptor}"
        ))
        add(decoroutinatorAwakeLabel)
        add(FrameNode(Opcodes.F_SAME, 0, null, 0, null))
        add(VarInsnNode(Opcodes.ALOAD, 0))
        add(VarInsnNode(Opcodes.ALOAD, 1))
        add(MethodInsnNode(
            Opcodes.INVOKESTATIC,
            Type.getInternalName(providerApiClass),
            ::awakeBaseContinuation.name,
            "(${Type.getDescriptor(Object::class.java)}${Type.getDescriptor(Object::class.java)})${Type.VOID_TYPE.descriptor}"
        ))
        add(InsnNode(Opcodes.RETURN))
        add(defaultAwakeLabel)
        add(FrameNode(Opcodes.F_SAME, 0, null, 0, null))
    })

    val annotations = baseContinuation.visibleAnnotations ?: (mutableListOf<AnnotationNode>().also {
        baseContinuation.visibleAnnotations = it
    })
    annotations.add(ClassTransformationInfo(
        fileName = null,
        lineNumbersByMethod = emptyMap(),
        baseContinuationInternalClassNames = emptySet()
    ).transformedAnnotation)
}

fun getDebugMetadataInfoFromClassBody(body: InputStream): DebugMetadataInfo? =
    getClassNode(body, skipCode = true)?.debugMetadataInfo

fun getDebugMetadataInfoFromClass(clazz: Class<*>): DebugMetadataInfo? =
    clazz.getDeclaredAnnotation(DebugMetadata::class.java)?.let {
        DebugMetadataInfo(
            specClassInternalClassName = it.className.internalName,
            baseContinuationInternalClassName = clazz.name.internalName,
            methodName = it.methodName,
            fileName = it.sourceFile.ifEmpty { null },
            lineNumbers = it.lineNumbers.toSet()
        )
    }

private val noTransformationStatus = TransformationStatus(
    updatedBody = null,
    needReadProviderModule = false
)

private val readProviderTransformationStatus = TransformationStatus(
    updatedBody = null,
    needReadProviderModule = true
)

private data class ClassTransformationInfo(
    val fileName: String?,
    val lineNumbersByMethod: Map<String, Set<Int>>,
    val baseContinuationInternalClassNames: Set<String>
)

private fun ClassNode.transform(classTransformationInfo: ClassTransformationInfo) {
    version = maxOf(version, Opcodes.V1_7)
    classTransformationInfo.lineNumbersByMethod.forEach { (methodName, lineNumbers) ->
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
    visibleAnnotations.add(classTransformationInfo.transformedAnnotation)
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

private val ClassNode.decoroutinatorTransformedAnnotation: AnnotationNode?
    get() = visibleAnnotations
        .orEmpty()
        .firstOrNull { it.desc == Type.getDescriptor(DecoroutinatorTransformed::class.java) }

private fun AnnotationNode.getField(name: String): Any? {
    var index = 0
    while (index < values.orEmpty().size) {
        if (values[index] == name) {
            return values[index + 1]
        }
        index += 2
    }
    return null
}

private val ClassNode.decoroutinatorTransformedVersion: Int?
    get() {
        val meta = decoroutinatorTransformedAnnotation ?: return null
        return meta.getField(DecoroutinatorTransformed::version.name) as Int
    }

private fun ClassNode.getClassTransformationInfo(metadataResolver: (className: String) -> DebugMetadataInfo?): ClassTransformationInfo? {
    val lineNumbersByMethod = mutableMapOf<String, MutableSet<Int>>()
    val fileNames = mutableSetOf<String>()
    val baseContinuationInternalClassNames = mutableSetOf<String>()
    val check = { info: DebugMetadataInfo? ->
        if (info != null && info.specClassInternalClassName == name) {
            val currentLineNumbers = lineNumbersByMethod.computeIfAbsent(info.methodName) {
                mutableSetOf(UNKNOWN_LINE_NUMBER)
            }
            currentLineNumbers.addAll(info.lineNumbers)
            if (info.fileName != null) {
                fileNames.add(info.fileName)
            }
            baseContinuationInternalClassNames.add(info.baseContinuationInternalClassName)
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
    return if (lineNumbersByMethod.isNotEmpty()) {
        val fileName = fileNames.run { when {
            isEmpty() -> null
            size == 1 -> single()
            else -> throw IllegalStateException(
                "class [$name] contains suspend fun metadata with multiple file names: [$this]"
            )
        } }
        ClassTransformationInfo(
            fileName = fileName,
            lineNumbersByMethod = lineNumbersByMethod,
            baseContinuationInternalClassNames = baseContinuationInternalClassNames
        )
    } else {
        null
    }
}

@Suppress("UNCHECKED_CAST")
private val ClassNode.debugMetadataInfo: DebugMetadataInfo?
    get() {
        visibleAnnotations?.forEach { annotation ->
            if (annotation.desc == Type.getDescriptor(DebugMetadata::class.java)) {
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
                    specClassInternalClassName = internalClassName,
                    baseContinuationInternalClassName = name,
                    methodName = methodName,
                    fileName = fileName,
                    lineNumbers = lineNumbers
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
        MethodHandles::lookup.name,
        "()${Type.getDescriptor(MethodHandles.Lookup::class.java)}"
    ))
    add(MethodInsnNode(
        Opcodes.INVOKESTATIC,
        Type.getInternalName(providerApiClass),
        ::registerTransformedClass.name,
        "(${Type.getDescriptor(MethodHandles.Lookup::class.java)})V"
    ))
}

private val ClassTransformationInfo.transformedAnnotation: AnnotationNode
    get() {
        val result = AnnotationNode(Opcodes.ASM9, Type.getDescriptor(DecoroutinatorTransformed::class.java))
        val lineNumbers = lineNumbersByMethod.entries.toList()
        result.values = buildList {
            if (fileName != null) {
                add(DecoroutinatorTransformed::fileName.name)
                add(fileName)
            } else {
                add(DecoroutinatorTransformed::fileNamePresent.name)
                add(false)
            }
            add(DecoroutinatorTransformed::methodNames.name)
            add(lineNumbers.map { it.key })

            add(DecoroutinatorTransformed::lineNumbersCounts.name)
            add(lineNumbers.map { it.value.size })

            add(DecoroutinatorTransformed::lineNumbers.name)
            add(lineNumbers.flatMap { it.value })

            add(DecoroutinatorTransformed::baseContinuationClasses.name)
            add(baseContinuationInternalClassNames.map { Type.getObjectType(it) })

            add(DecoroutinatorTransformed::version.name)
            add(TRANSFORMED_VERSION)
        }
        return result
    }

private val ClassNode.classBody: ByteArray
    get() {
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        accept(writer)
        return writer.toByteArray()
    }
