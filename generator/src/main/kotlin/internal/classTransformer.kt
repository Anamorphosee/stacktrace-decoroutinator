@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.generator.internal

import dev.reformator.stacktracedecoroutinator.common.internal.*
import dev.reformator.stacktracedecoroutinator.intrinsics.DebugMetadata
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.*
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import java.io.InputStream
import java.lang.invoke.MethodHandles
import java.lang.reflect.Method
import kotlin.coroutines.Continuation

class ClassBodyTransformationStatus(
    val updatedBody: ByteArray?,
    val needReadProviderModule: Boolean
)

class NeedTransformationStatus(
    val needTransformation: Boolean,
    val needReadProviderModule: Boolean
)

val Class<*>.needTransformation: NeedTransformationStatus
    get() {
        getDeclaredAnnotation(DecoroutinatorTransformed::class.java)?.let { transformedAnnotation ->
            return if (transformedAnnotation.version < TRANSFORMED_VERSION) {
                fullNeedTransformationStatus
            } else if (transformedAnnotation.version == TRANSFORMED_VERSION) {
                readProviderNeedTransformationStatus
            } else {
                error("class [$name]'s transformed meta has version [${transformedAnnotation.version}]. " +
                        "Please update Decoroutinator")
            }
        }
        if (name == BASE_CONTINUATION_CLASS_NAME) {
            return fullNeedTransformationStatus
        }
        declaredMethods.forEach { method ->
            if (method.isSuspend) {
                return fullNeedTransformationStatus
            }
        }
        return noNeedTransformationStatus
    }

fun transformClassBody(
    classBody: InputStream,
    metadataResolver: (className: String) -> DebugMetadataInfo?
): ClassBodyTransformationStatus {
    val node = getClassNode(classBody) ?: return noClassBodyTransformationStatus
    val version = node.decoroutinatorTransformedVersion
    if (version != null) {
        if (version == TRANSFORMED_VERSION) {
            return readProviderClassBodyTransformationStatus
        }
        if (version > TRANSFORMED_VERSION) {
            error("class [${node.name}]'s transformed meta has version [$version]. Please update Decoroutinator")
        }
    }
    if (node.name == BASE_CONTINUATION_CLASS_NAME.internalName) {
        transformBaseContinuation(node)
        return ClassBodyTransformationStatus(
            updatedBody = node.classBody,
            needReadProviderModule = true
        )
    }
    val transformationInfo = node.getClassTransformationInfo(metadataResolver) ?: return noClassBodyTransformationStatus
    node.transform(transformationInfo)
    return ClassBodyTransformationStatus(
        updatedBody = node.classBody,
        needReadProviderModule = true
    )
}

fun addReadProviderModuleToModuleInfo(moduleInfoBody: InputStream): ByteArray? {
    val node = getClassNode(moduleInfoBody) ?: return null
    val module = node.module ?: return null
    val requires: MutableList<ModuleRequireNode> = module.requires ?: run {
        val newRequires = mutableListOf<ModuleRequireNode>()
        module.requires = newRequires
        newRequires
    }
    if (requires.any { it.module == PROVIDER_MODULE_NAME }) return null
    requires.add(ModuleRequireNode(
        PROVIDER_MODULE_NAME,
        Opcodes.ACC_SYNTHETIC,
        null
    ))
    return node.classBody
}

private fun transformBaseContinuation(baseContinuation: ClassNode) {
    val resumeWithMethod = baseContinuation.methods?.find {
        it.desc == "(${Type.getDescriptor(Object::class.java)})${Type.VOID_TYPE.descriptor}" && !it.isStatic
    } ?: error("[${BaseContinuation::resumeWith.name}] method is not found")

    resumeWithMethod.instructions.insertBefore(resumeWithMethod.instructions.first, InsnList().apply {
        add(MethodInsnNode(
            Opcodes.INVOKESTATIC,
            Type.getInternalName(providerApiClass),
            IS_DECOROUTINATOR_ENABLED_METHOD_NAME,
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
            GET_COOKIE_METHOD_NAME,
            "()${Type.getDescriptor(Object::class.java)}"
        ))
        add(InsnNode(Opcodes.DUP))
        val decoroutinatorAwakeLabel = LabelNode()
        add(JumpInsnNode(
            Opcodes.IFNONNULL,
            decoroutinatorAwakeLabel
        ))
        add(InsnNode(Opcodes.POP))
        add(MethodInsnNode(
            Opcodes.INVOKESTATIC,
            Type.getInternalName(MethodHandles::class.java),
            MethodHandles::lookup.name,
            "()${Type.getDescriptor(MethodHandles.Lookup::class.java)}"
        ))
        add(MethodInsnNode(
            Opcodes.INVOKESTATIC,
            Type.getInternalName(providerApiClass),
            ::prepareCookie.name,
            "(${Type.getDescriptor(MethodHandles.Lookup::class.java)})${Type.getDescriptor(Object::class.java)}"
        ))
        add(decoroutinatorAwakeLabel)
        add(FrameNode(Opcodes.F_SAME1, 0, null, 1, arrayOf(Type.getInternalName(Object::class.java))))
        add(VarInsnNode(Opcodes.ALOAD, 0))
        add(VarInsnNode(Opcodes.ALOAD, 1))
        add(MethodInsnNode(
            Opcodes.INVOKESTATIC,
            Type.getInternalName(providerApiClass),
            ::awakeBaseContinuation.name,
            "(${Type.getDescriptor(Object::class.java)}${Type.getDescriptor(Object::class.java)}${Type.getDescriptor(Object::class.java)})${Type.VOID_TYPE.descriptor}"
        ))
        add(InsnNode(Opcodes.RETURN))
        add(defaultAwakeLabel)
        add(FrameNode(Opcodes.F_SAME, 0, null, 0, null))
    })

    val annotations = baseContinuation.visibleAnnotations ?: (mutableListOf<AnnotationNode>().also {
        baseContinuation.visibleAnnotations = it
    })
    annotations.add(ClassTransformationInfo(
        lineNumbersByMethod = emptyMap(),
        baseContinuationInternalClassNames = emptySet()
    ).getTransformedAnnotation(baseContinuation))
}

fun getDebugMetadataInfoFromClassBody(body: InputStream): DebugMetadataInfo? =
    getClassNode(body, skipCode = true)?.debugMetadataInfo

fun getDebugMetadataInfoFromClass(clazz: Class<*>): DebugMetadataInfo? =
    clazz.getDeclaredAnnotation(DebugMetadata::class.java)?.let {
        DebugMetadataInfo(
            specClassInternalClassName = it.c.internalName,
            baseContinuationInternalClassName = clazz.name.internalName,
            methodName = it.m,
            lineNumbers = it.l.toSet()
        )
    }

private val noClassBodyTransformationStatus = ClassBodyTransformationStatus(
    updatedBody = null,
    needReadProviderModule = false
)

private val readProviderClassBodyTransformationStatus = ClassBodyTransformationStatus(
    updatedBody = null,
    needReadProviderModule = true
)

private val fullNeedTransformationStatus = NeedTransformationStatus(
    needTransformation = true,
    needReadProviderModule = true
)

private val readProviderNeedTransformationStatus = NeedTransformationStatus(
    needTransformation = false,
    needReadProviderModule = true
)

private val noNeedTransformationStatus = NeedTransformationStatus(
    needTransformation = false,
    needReadProviderModule = false
)

private const val PROVIDER_MODULE_NAME = "dev.reformator.stacktracedecoroutinator.provider"

private data class ClassTransformationInfo(
    val lineNumbersByMethod: Map<String, Set<Int>>,
    val baseContinuationInternalClassNames: Set<String>
)

private fun ClassNode.transform(classTransformationInfo: ClassTransformationInfo) {
    val isInterface = access and Opcodes.ACC_INTERFACE != 0
    val isPrivateMethodsInInterfacesSupported = version >= Opcodes.V9
    val makePrivate = !isInterface || isPrivateMethodsInInterfacesSupported
    val makeFinal = !isInterface
    version = maxOf(version, Opcodes.V1_7)
    classTransformationInfo.lineNumbersByMethod.forEach { (methodName, lineNumbers) ->
        methods.add(buildSpecMethodNode(
            methodName = methodName,
            lineNumbers = lineNumbers,
            makePrivate = makePrivate,
            makeFinal = makeFinal
        ))
    }

    val clinit = getOrCreateClinitMethod()
    clinit.instructions.insertBefore(
        clinit.instructions.first,
        buildCallRegisterLookupInstructions()
    )
    if (visibleAnnotations == null) {
        visibleAnnotations = mutableListOf()
    }
    visibleAnnotations.add(classTransformationInfo.getTransformedAnnotation(this))
}

internal fun getClassNode(classBody: InputStream, skipCode: Boolean = false): ClassNode? {
    return try {
        val classReader = ClassReader(classBody)
        val classNode = ClassNode(Opcodes.ASM9)
        classReader.accept(classNode, if (skipCode) ClassReader.SKIP_CODE else 0)
        classNode
    } catch (_: Exception) {
        null
    }
}

internal val ClassNode.decoroutinatorTransformedAnnotation: AnnotationNode?
    get() = visibleAnnotations
        .orEmpty()
        .firstOrNull { it.desc == Type.getDescriptor(DecoroutinatorTransformed::class.java) }

internal val ClassNode.kotlinDebugMetadataAnnotation: AnnotationNode?
    get() = visibleAnnotations
        .orEmpty()
        .firstOrNull { it.desc == Type.getDescriptor(DebugMetadata::class.java) }

internal fun AnnotationNode.getField(name: String): Any? {
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
    val baseContinuationInternalClassNames = mutableSetOf<String>()
    val check = { info: DebugMetadataInfo? ->
        if (info != null && info.specClassInternalClassName == name) {
            val currentLineNumbers = lineNumbersByMethod.computeIfAbsent(info.methodName) {
                mutableSetOf(UNKNOWN_LINE_NUMBER)
            }
            currentLineNumbers.addAll(info.lineNumbers)
            baseContinuationInternalClassNames.add(info.baseContinuationInternalClassName)
        }
    }
    check(debugMetadataInfo)
    methods.orEmpty().forEach { method ->
        when (val status = getCheckTransformationStatus(this, method)) {
            is DefaultTransformationStatus -> check(metadataResolver(status.baseContinuationClassName))
            is TailCallTransformationStatus -> tailCallDeopt(
                completionVarIndex = status.completionVarIndex,
                clazz = this,
                method = method,
                lineNumbersByMethod = lineNumbersByMethod,
            )
            null -> { }
        }
    }
    return if (lineNumbersByMethod.isNotEmpty()) {
        ClassTransformationInfo(
            lineNumbersByMethod = lineNumbersByMethod,
            baseContinuationInternalClassNames = baseContinuationInternalClassNames
        )
    } else {
        null
    }
}

private fun tailCallDeopt(
    completionVarIndex: Int,
    clazz: ClassNode,
    method: MethodNode,
    lineNumbersByMethod: MutableMap<String, MutableSet<Int>>,
) {
    method.instructions.forEach { instruction ->
        if (instruction is VarInsnNode && instruction.opcode == Opcodes.ALOAD && instruction.`var` == completionVarIndex) {
            val lineNumber = generateSequence(instruction.previous, { it.previous })
                .mapNotNull { it as? LineNumberNode }
                .firstOrNull()?.line ?: UNKNOWN_LINE_NUMBER
            val currentLineNumbers = lineNumbersByMethod.computeIfAbsent(method.name) {
                mutableSetOf(UNKNOWN_LINE_NUMBER)
            }
            currentLineNumbers.add(lineNumber)
            method.instructions.insert(instruction, InsnList().apply {
                if (clazz.sourceFile != null) {
                    add(LdcInsnNode(clazz.sourceFile))
                } else {
                    add(InsnNode(Opcodes.ACONST_NULL))
                }
                add(LdcInsnNode(Type.getObjectType(clazz.name).className))
                add(LdcInsnNode(method.name))
                add(LdcInsnNode(lineNumber))
                add(MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(providerApiClass),
                    ::getBaseContinuation.name,
                    "(" +
                            Type.getDescriptor(Object::class.java) +
                            Type.getDescriptor(String::class.java) +
                            Type.getDescriptor(String::class.java) +
                            Type.getDescriptor(String::class.java) +
                            Type.INT_TYPE.descriptor +
                            ")${Type.getDescriptor(Object::class.java)}"
                ))
                add(TypeInsnNode(Opcodes.CHECKCAST, Type.getInternalName(Continuation::class.java)))
            })
        }
    }
}

@Suppress("UNCHECKED_CAST")
private val ClassNode.debugMetadataInfo: DebugMetadataInfo?
    get() = kotlinDebugMetadataAnnotation?.let { annotation ->
        val internalClassName = (annotation.getField(DebugMetadata::c.name) as String).internalName
        val methodName = annotation.getField(DebugMetadata::m.name) as String
        val lineNumbers = (annotation.getField(DebugMetadata::l.name) as List<Int>).toSet()
        if (lineNumbers.isEmpty()) {
            return null
        }
        DebugMetadataInfo(
            specClassInternalClassName = internalClassName,
            baseContinuationInternalClassName = name,
            methodName = methodName,
            lineNumbers = lineNumbers
        )
    }

private val MethodNode.lastArgumentIndex: Int
    get() {
        var result = 0
        if (!isStatic) {
            result++
        }
        val arguments = Type.getArgumentTypes(desc)
        arguments.asSequence()
            .take(arguments.size - 1)
            .forEach { result += it.size }
        return result
    }

private sealed interface CheckTransformationStatus
private class DefaultTransformationStatus(val baseContinuationClassName: String): CheckTransformationStatus
private class TailCallTransformationStatus(val completionVarIndex: Int): CheckTransformationStatus

private fun getCheckTransformationStatus(clazz: ClassNode, method: MethodNode): CheckTransformationStatus? {
    if (
        method.name == "<init>"
        || method.name == "<clinit>"
        || !method.desc.endsWith("${Type.getDescriptor(Continuation::class.java)})${Type.getDescriptor(Object::class.java)}")
        || method.instructions == null
        || method.instructions.size() == 0
    ) {
        return null
    }
    val completionIndex = method.lastArgumentIndex
    val baseContinuationClassNames = mutableSetOf<String>()
    method.instructions.forEach { instruction ->
        if (instruction is VarInsnNode) {
            when (instruction.opcode) {
                Opcodes.ISTORE, Opcodes.FSTORE, Opcodes.ASTORE -> {
                    if (instruction.`var` == completionIndex) return null
                }
                Opcodes.LSTORE, Opcodes.DSTORE -> {
                    if (instruction.`var` == completionIndex || instruction.`var` == completionIndex - 1) {
                        return null
                    }
                }
                Opcodes.ALOAD -> {
                    if (instruction.`var` == completionIndex) {
                        var next = instruction.next
                        while (next != null) {
                            if (next.opcode == -1) {
                                next = next.next
                            } else if (next is TypeInsnNode) {
                                if (next.opcode == Opcodes.INSTANCEOF || next.opcode == Opcodes.CHECKCAST) {
                                    baseContinuationClassNames.add(Type.getObjectType(next.desc).className)
                                    break
                                }
                            } else if (next is MethodInsnNode) {
                                if (next.opcode == Opcodes.INVOKESPECIAL && next.name == "<init>"
                                    && next.desc == "(${Type.getDescriptor(Continuation::class.java)})${Type.VOID_TYPE.descriptor}"
                                ) {
                                    baseContinuationClassNames.add(Type.getObjectType(next.owner).className)
                                }
                                break
                            } else {
                                break
                            }
                        }
                    }
                }
            }
        }
    }
    return if (baseContinuationClassNames.size == 1) {
        val baseContinuationClassName = baseContinuationClassNames.single()
        if (baseContinuationClassName != Type.getObjectType(clazz.name).className) {
            DefaultTransformationStatus(baseContinuationClassName)
        } else {
            null
        }
    } else if (baseContinuationClassNames.isEmpty()) {
        val hasCompletionLocalVar = method.localVariables.orEmpty().any { localVar ->
            localVar.index == completionIndex && localVar.name == "\$completion"
        }
        val hasContinuationLocalVar = method.localVariables.orEmpty().any { localVar ->
            localVar.name == "\$continuation"
        }
        val isMethodSynthetic = method.access and Opcodes.ACC_SYNTHETIC != 0
        if (hasCompletionLocalVar && !hasContinuationLocalVar && !isMethodSynthetic) {
            TailCallTransformationStatus(completionIndex)
        } else {
            null
        }
    } else {
        null
    }
}

private val Method.isSuspend: Boolean
    get() = parameters.isNotEmpty() && parameters.last().type == Continuation::class.java && returnType == Object::class.java

private val MethodNode.isStatic: Boolean
    get() = access and Opcodes.ACC_STATIC != 0

private fun ClassNode.getOrCreateClinitMethod(): MethodNode =
    methods.firstOrNull {
        it.name == "<clinit>" && it.desc == "()${Type.VOID_TYPE.descriptor}" && it.isStatic
    } ?: MethodNode(Opcodes.ASM9).apply {
        name = "<clinit>"
        desc = "()${Type.VOID_TYPE.descriptor}"
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

private fun ClassTransformationInfo.getTransformedAnnotation(clazz: ClassNode): AnnotationNode {
    val result = AnnotationNode(Opcodes.ASM9, Type.getDescriptor(DecoroutinatorTransformed::class.java))
    val lineNumbers = lineNumbersByMethod.entries.toList()
    result.values = buildList {
        if (clazz.sourceFile != null) {
            add(DecoroutinatorTransformed::fileName.name)
            add(clazz.sourceFile)
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
