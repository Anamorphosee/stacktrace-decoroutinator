package dev.reformator.stacktracedecoroutinator.jvmagentcommon

import dev.reformator.stacktracedecoroutinator.common.isDecoroutinatorBaseContinuation
import dev.reformator.stacktracedecoroutinator.jvmcommon.buildStacktraceMethodNode
import dev.reformator.stacktracedecoroutinator.jvmcommon.loadDecoroutinatorBaseContinuationClassBody
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import java.lang.instrument.ClassFileTransformer
import java.lang.invoke.MethodHandles
import java.security.ProtectionDomain

internal object DecoroutinatorBaseContinuationClassFileTransformer: ClassFileTransformer {
    override fun transform(
        loader: ClassLoader?,
        internalClassName: String,
        classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain?,
        classBody: ByteArray
    ): ByteArray? {
        if (internalClassName != BASE_CONTINUATION_INTERNAL_CLASS_NAME) {
            return null
        }
        return if (classBeingRedefined == null) {
            if (decoroutinatorJvmAgentRegistry.isBaseContinuationTransformationAllowed) {
                loadDecoroutinatorBaseContinuationClassBody()
            } else {
                null
            }
        } else {
            if (
                !classBeingRedefined.isDecoroutinatorBaseContinuation
                && decoroutinatorJvmAgentRegistry.isBaseContinuationRetransformationAllowed
            ) {
                loadDecoroutinatorBaseContinuationClassBody()
            } else {
                null
            }
        }
    }
}

internal object DecoroutinatorClassFileTransformer: ClassFileTransformer {
    override fun transform(
        loader: ClassLoader?,
        internalClassName: String,
        classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain?,
        classBody: ByteArray
    ): ByteArray? {
        if (
            internalClassName.startsWith("java/")
            || internalClassName == BASE_CONTINUATION_INTERNAL_CLASS_NAME
        ) {
            return null
        }
        if (classBeingRedefined != null) {
            if (
                !decoroutinatorJvmAgentRegistry.isRetransformationAllowed
                || classBeingRedefined.isDecoroutinatorAgentTransformed
            ) {
                return null
            }
        } else if (!decoroutinatorJvmAgentRegistry.isTransformationAllowed) {
            return null
        }
        val classNode = getClassNode(classBody)
        if (classNode.isTransformed()) {
            return null
        }
        val metadataInfo = getMetadataInfo(classNode) ?: return null
        classNode.transform(metadataInfo)
        return getClassBody(classNode)
    }
}

private data class MetadataInfo(
    val fileName: String?,
    val suspendFuncName2LineNumbers: Map<String, Set<Int>>
)

private fun getClassBody(classNode: ClassNode): ByteArray {
    val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
    classNode.accept(writer)
    return writer.toByteArray()
}

private fun getClassNode(classBody: ByteArray): ClassNode {
    val classReader = ClassReader(classBody)
    val classNode = ClassNode(Opcodes.ASM9)
    classReader.accept(classNode, 0)
    return classNode
}

private fun ClassNode.isTransformed(): Boolean =
    visibleAnnotations
        .orEmpty()
        .find { it.desc == Type.getDescriptor(DecoroutinatorAgentTransformedMarker::class.java) } != null

private fun getMetadataInfo(classNode: ClassNode): MetadataInfo? {
    val suspendFunc2LineNumbers = mutableMapOf<String, MutableSet<Int>>()
    val fileNames = mutableSetOf<String>()
    val check = { info: DebugMetadataInfo? ->
        if (info != null && info.internalClassName == classNode.name && info.lineNumbers.isNotEmpty()) {
            val currentLineNumbers = suspendFunc2LineNumbers.computeIfAbsent(info.methodName) {
                mutableSetOf()
            }
            currentLineNumbers.addAll(info.lineNumbers)
            if (info.fileName != null) {
                fileNames.add(info.fileName)
            }
        }
    }
    check(classNode.getDebugMetadataInfo())
    classNode.methods.orEmpty().forEach { method ->
        if (method.hasCode && method.isSuspend) {
            check(method.getDebugMetadataInfo())
        }
    }
    return if (suspendFunc2LineNumbers.isNotEmpty()) {
        val fileName = fileNames.run { when {
            isEmpty() -> null
            size == 1 -> single()
            else -> throw IllegalStateException(
                "class [${classNode.name}] contains suspend fun metadata with multiple file names: [$this]"
            )
        } }
        MetadataInfo(fileName, suspendFunc2LineNumbers)
    } else {
        null
    }
}

private fun MethodNode.getDebugMetadataInfo(): DebugMetadataInfo? {
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
    val firstInstructions: List<AbstractInsnNode> = instructions.asSequence()
        .filter { it.opcode != -1 && it.opcode != Opcodes.NOP }
        .take(2)
        .toList()
    if (firstInstructions.size == 2) {
        val isAloadContinuation = firstInstructions[0].let {
            it is VarInsnNode && it.opcode == Opcodes.ALOAD && it.`var` == continuationIndex
        }
        val continuationClassName = firstInstructions[1].let {
            if (it is TypeInsnNode && it.opcode == Opcodes.INSTANCEOF) {
                it.desc.replace('/', '.')
            } else {
                null
            }
        }
        if (isAloadContinuation && continuationClassName != null) {
            return decoroutinatorJvmAgentRegistry.metadataInfoResolver.getDebugMetadataInfo(continuationClassName)
        }
    }
    return null
}

private val MethodNode.hasCode: Boolean
    get() = instructions != null && instructions.size() > 0

private val MethodNode.isSuspend: Boolean
    get() = desc.endsWith("Lkotlin/coroutines/Continuation;)Ljava/lang/Object;")

private fun ClassNode.transform(metadataInfo: MetadataInfo) {
    version = maxOf(version, Opcodes.V1_8)
    metadataInfo.suspendFuncName2LineNumbers.forEach { (methodName, lineNumbers) ->
        methods.add(buildStacktraceMethodNode(methodName, lineNumbers, true))
    }
    methods.add(buildRegisterLookupMethod())
    val clinit = getOrCreateClinitMethod()
    clinit.instructions.insertBefore(
        clinit.instructions.first,
        buildCallRegisterLookupInstruction(name)
    )
    if (visibleAnnotations == null) {
        visibleAnnotations = mutableListOf()
    }
    visibleAnnotations.add(buildMarkerAnnotation(metadataInfo))
}

private fun buildMarkerAnnotation(metadataInfo: MetadataInfo): AnnotationNode {
    val result = AnnotationNode(Opcodes.ASM9, Type.getDescriptor(DecoroutinatorAgentTransformedMarker::class.java))
    val list = metadataInfo.suspendFuncName2LineNumbers.entries.toList()
    result.values = buildList {
        if (metadataInfo.fileName != null) {
            add("fileName")
            add(metadataInfo.fileName)
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

private fun buildCallRegisterLookupInstruction(internalClassName: String): MethodInsnNode = MethodInsnNode(
    Opcodes.INVOKESTATIC,
    internalClassName,
    REGISTER_LOOKUP_METHOD_NAME,
    "()V"
)

private fun buildRegisterLookupMethod(): MethodNode {
    val method = MethodNode(Opcodes.ASM9).apply {
        access = Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL or Opcodes.ACC_SYNTHETIC
        name = REGISTER_LOOKUP_METHOD_NAME
        desc = "()V"
    }
    method.instructions.apply {
        add(MethodInsnNode(
            Opcodes.INVOKESTATIC,
            Type.getInternalName(MethodHandles::class.java),
            "lookup",
            "()${Type.getDescriptor(MethodHandles.Lookup::class.java)}"
        ))
        add(MethodInsnNode(
            Opcodes.INVOKESTATIC,
            registerLookupInternalClassName,
            registerLookupMethodName,
            "(${Type.getDescriptor(MethodHandles.Lookup::class.java)})V"
        ))
        add(InsnNode(Opcodes.RETURN))
    }
    return method
}

private fun ClassNode.getOrCreateClinitMethod(): MethodNode {
    val result = methods.firstOrNull {
        it.name == "<clinit>" && it.desc == "()V" && it.isStatic
    }
    if (result == null) {
        val result = MethodNode(Opcodes.ASM9)
        result.name = "<clinit>"
        result.desc = "()V"
        result.access = Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC
        result.instructions.add(InsnNode(Opcodes.RETURN))
        methods.add(result)
        return result
    }
    return result
}

private val MethodNode.isStatic: Boolean
    get() = access and Opcodes.ACC_STATIC != 0

