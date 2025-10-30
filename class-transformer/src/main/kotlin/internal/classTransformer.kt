@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.classtransformer.internal

import dev.reformator.bytecodeprocessor.intrinsics.LoadConstant
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.kmetarepack.isInline
import dev.reformator.kmetarepack.isSuspend
import dev.reformator.kmetarepack.jvm.JvmMethodSignature
import dev.reformator.kmetarepack.jvm.KotlinClassMetadata
import dev.reformator.kmetarepack.jvm.signature
import dev.reformator.kmetarepack.jvm.Metadata as createMetadata
import dev.reformator.stacktracedecoroutinator.intrinsics.BASE_CONTINUATION_CLASS_NAME
import dev.reformator.stacktracedecoroutinator.intrinsics.DebugMetadata
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.intrinsics.LABEL_FIELD_NAME
import dev.reformator.stacktracedecoroutinator.intrinsics.UNKNOWN_LINE_NUMBER
import dev.reformator.stacktracedecoroutinator.provider.BaseContinuationExtractor
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorTransformed
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessor
import dev.reformator.stacktracedecoroutinator.provider.internal.internalName
import dev.reformator.stacktracedecoroutinator.provider.internal.providerInternalApiClass
import dev.reformator.stacktracedecoroutinator.provider.providerApiClass
import dev.reformator.stacktracedecoroutinator.specmethodbuilder.internal.buildSpecMethodNode
import dev.reformator.stacktracedecoroutinator.specmethodbuilder.internal.decoroutinatorTransformedAnnotation
import dev.reformator.stacktracedecoroutinator.specmethodbuilder.internal.getClassNode
import dev.reformator.stacktracedecoroutinator.specmethodbuilder.internal.getField
import dev.reformator.stacktracedecoroutinator.specmethodbuilder.internal.kotlinDebugMetadataAnnotation
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
            if (transformedAnnotation.skipSpecMethods) {
                error("not implemented")
            }
            return readProviderNeedTransformationStatus
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
    metadataResolver: (className: String) -> DebugMetadataInfo?,
    skipSpecMethods: Boolean
): ClassBodyTransformationStatus {
    val node = getClassNode(classBody) ?: return noClassBodyTransformationStatus
    node.decoroutinatorTransformedAnnotation?.let { transformedAnnotation ->
        val annotationSkipSpecMethods =
            transformedAnnotation.getField(DecoroutinatorTransformed::skipSpecMethods.name) as Boolean?
        if (skipSpecMethods != (annotationSkipSpecMethods ?: false)) {
            error("class [${node.name}] is already transformed but skipSpecMethods = [$annotationSkipSpecMethods]")
        }
        return readProviderClassBodyTransformationStatus
    }

    var doTransformation = false
    val lineNumbersBySpecMethodName: MutableMap<String, MutableSet<Int>> = HashMap()

    val metadataAnnotation = node.kotlinMetadataAnnotation ?: return noClassBodyTransformationStatus
    val xi = metadataAnnotation.getField("xi") as Int?
    // 7th bit of 'xi' indicates that the class is a scope of an inline function
    if (xi == null || xi and (1 shl 7) == 0) {
        if (node.name == BASE_CONTINUATION_CLASS_NAME.internalName) {
            node.transformBaseContinuation()
            doTransformation = true
        } else {
            if (node.tryAddBaseContinuationExtractor()) doTransformation = true

            @Suppress("UNCHECKED_CAST")
            val notSuspendFunctionSignatures = createMetadata(
                kind = metadataAnnotation.getField("k") as Int?,
                metadataVersion = (metadataAnnotation.getField("mv") as List<Int>?)?.toIntArray(),
                data1 = (metadataAnnotation.getField("d1") as List<String>?)?.toTypedArray(),
                data2 = (metadataAnnotation.getField("d2") as List<String>?)?.toTypedArray(),
                extraString = metadataAnnotation.getField("xs") as String?,
                packageName = metadataAnnotation.getField("pn") as String?,
                extraInt = xi
            ).getNonSuspendFunctionSignatures()

            if (node.tryTransformSuspendMethods(
                    metadataResolver = metadataResolver,
                    lineNumbersBySpecMethodName = lineNumbersBySpecMethodName,
                    notSuspendFunctionSignatures = notSuspendFunctionSignatures
            )) {
                doTransformation = true
            }
        }
    }
    return if (doTransformation) {
        node.generateSpecMethodsAndTransformAnnotation(skipSpecMethods, lineNumbersBySpecMethodName)
        ClassBodyTransformationStatus(
            updatedBody = node.classBody,
            needReadProviderModule = true
        )
    } else noClassBodyTransformationStatus
}

fun getDebugMetadataInfoFromClassBody(body: InputStream): DebugMetadataInfo? =
    getClassNode(body, skipCode = true)?.debugMetadataInfo

fun getDebugMetadataInfoFromClass(clazz: Class<*>): DebugMetadataInfo? =
    clazz.getDeclaredAnnotation(DebugMetadata::class.java)?.let {
        DebugMetadataInfo(
            specClassInternalClassName = it.c.internalName,
            methodName = it.m,
            lineNumbers = it.l.toSet()
        )
    }

private val isDecoroutinatorEnabledMethodName: String
    @LoadConstant("isDecoroutinatorEnabledMethodName") get() = fail()

private val getBaseContinuationAccessorMethodName: String
    @LoadConstant("getBaseContinuationAccessorMethodName") get() = fail()

private val prepareBaseContinuationAccessorMethodName: String
    @LoadConstant("prepareBaseContinuationAccessorMethodName") get() = fail()

private val awakeBaseContinuationMethodName: String
    @LoadConstant("awakeBaseContinuationMethodName") get() = fail()

private val isUsingElementFactoryForBaseContinuationEnabledMethodName: String
    @LoadConstant("isUsingElementFactoryForBaseContinuationEnabledMethodName") get() = fail()

private val getElementFactoryStacktraceElementMethodName: String
    @LoadConstant("getElementFactoryStacktraceElementMethodName") get() = fail()

private val baseContinuationExtractorGetLabelMethodName: String
    @LoadConstant("baseContinuationExtractorGetLabelMethodName") get() = fail()

private val baseContinuationExtractorGetElementsMethodName: String
    @LoadConstant("baseContinuationExtractorGetElementsMethodName") get() = fail()

private const val baseContinuationElementsFieldName = "\$decoroutinator\$elements"

private fun Metadata.getNonSuspendFunctionSignatures(): List<JvmMethodSignature> {
    val functions = when(val metadata = KotlinClassMetadata.readLenient(this)) {
        is KotlinClassMetadata.Class -> metadata.kmClass.functions
        is KotlinClassMetadata.FileFacade -> metadata.kmPackage.functions
        is KotlinClassMetadata.SyntheticClass -> metadata.kmLambda?.function?.let { listOf(it) } ?: emptyList()
        is KotlinClassMetadata.MultiFileClassPart -> metadata.kmPackage.functions
        is KotlinClassMetadata.MultiFileClassFacade -> emptyList()
        is KotlinClassMetadata.Unknown -> emptyList()
    }
    return functions.asSequence()
        .filter { it.isInline || !it.isSuspend }
        .mapNotNull { it.signature }
        .filter { it.descriptor.endsWith("${Type.getDescriptor(Continuation::class.java)})${Type.getDescriptor(Object::class.java)}") }
        .toList()
}

private fun ClassNode.tryAddBaseContinuationExtractor(): Boolean {
    val debugMetadata = kotlinDebugMetadataAnnotation ?: return false

    if (
        access and Opcodes.ACC_INTERFACE != 0 || fields.orEmpty().all { field ->
            field.name != LABEL_FIELD_NAME
            || field.desc != Type.INT_TYPE.descriptor
            || field.access and Opcodes.ACC_STATIC != 0
        }
    ) return false

    interfaces = interfaces.orEmpty() + Type.getInternalName(BaseContinuationExtractor::class.java)

    fields = fields.orEmpty() + FieldNode(
        Opcodes.ASM9,
        Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL or Opcodes.ACC_SYNTHETIC,
        baseContinuationElementsFieldName,
        Type.getDescriptor(Array<StackTraceElement>::class.java),
        null,
        null
    )

    methods = methods.orEmpty() + MethodNode(Opcodes.ASM9).apply {
        access = Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SYNTHETIC
        name = baseContinuationExtractorGetLabelMethodName
        desc = "()${Type.INT_TYPE.descriptor}"
        instructions = InsnList().apply {
            add(VarInsnNode(Opcodes.ALOAD, 0))
            add(FieldInsnNode(
                Opcodes.GETFIELD,
                this@tryAddBaseContinuationExtractor.name,
                LABEL_FIELD_NAME,
                Type.INT_TYPE.descriptor
            ))
            add(InsnNode(Opcodes.IRETURN))
        }
    } + MethodNode(Opcodes.ASM9).apply {
        access = Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SYNTHETIC
        name = baseContinuationExtractorGetElementsMethodName
        desc = "()${Type.getDescriptor(Array<StackTraceElement>::class.java)}"
        instructions = InsnList().apply {
            add(FieldInsnNode(
                Opcodes.GETSTATIC,
                this@tryAddBaseContinuationExtractor.name,
                baseContinuationElementsFieldName,
                Type.getDescriptor(Array<StackTraceElement>::class.java)
            ))
            add(InsnNode(Opcodes.ARETURN))
        }
    }

    getOrCreateClinitMethod().apply {
        val className = (debugMetadata.getField(DebugMetadata::c.name) as String?).orEmpty()
        val methodName = (debugMetadata.getField(DebugMetadata::m.name) as String?).orEmpty()
        val fileName = (debugMetadata.getField(DebugMetadata::f.name) as String?)?.takeIf { it.isNotEmpty() }
        @Suppress("UNCHECKED_CAST")
        val lineNumbers = (debugMetadata.getField(DebugMetadata::l.name) as List<Int>?).orEmpty()
        instructions.insertBefore(instructions.first, InsnList().apply {
            add(LdcInsnNode(lineNumbers.size + 1))
            add(TypeInsnNode(Opcodes.ANEWARRAY, Type.getInternalName(StackTraceElement::class.java)))
            repeat(lineNumbers.size + 1) { index ->
                add(InsnNode(Opcodes.DUP))
                add(LdcInsnNode(index))
                add(TypeInsnNode(Opcodes.NEW, Type.getInternalName(StackTraceElement::class.java)))
                add(InsnNode(Opcodes.DUP))
                add(LdcInsnNode(className))
                add(LdcInsnNode(methodName))
                add(if (fileName != null) LdcInsnNode(fileName) else InsnNode(Opcodes.ACONST_NULL))
                add(LdcInsnNode(if (index == 0) UNKNOWN_LINE_NUMBER else lineNumbers[index - 1]))
                add(MethodInsnNode(
                    Opcodes.INVOKESPECIAL,
                    Type.getInternalName(StackTraceElement::class.java),
                    "<init>",
                    "(${Type.getDescriptor(String::class.java)}${Type.getDescriptor(String::class.java)}"
                        + "${Type.getDescriptor(String::class.java)}${Type.INT_TYPE.descriptor})${Type.VOID_TYPE.descriptor}"
                ))
                add(InsnNode(Opcodes.AASTORE))
            }
            add(FieldInsnNode(
                Opcodes.PUTSTATIC,
                this@tryAddBaseContinuationExtractor.name,
                baseContinuationElementsFieldName,
                Type.getDescriptor(Array<StackTraceElement>::class.java)
            ))
        })
    }

    return true
}

private fun ClassNode.transformBaseContinuation() {
    val resumeWithMethod = methods?.find {
        it.desc == "(${Type.getDescriptor(Object::class.java)})${Type.VOID_TYPE.descriptor}" && !it.isStatic
    } ?: error("[${BaseContinuation::resumeWith.name}] method is not found")
    resumeWithMethod.instructions.insertBefore(resumeWithMethod.instructions.first, InsnList().apply {
        add(MethodInsnNode(
            Opcodes.INVOKESTATIC,
            Type.getInternalName(providerApiClass),
            isDecoroutinatorEnabledMethodName,
            "()${Type.BOOLEAN_TYPE.descriptor}"
        ))
        val defaultAwakeLabel = LabelNode()
        add(JumpInsnNode(
            Opcodes.IFEQ,
            defaultAwakeLabel
        ))
        add(MethodInsnNode(
            Opcodes.INVOKESTATIC,
            Type.getInternalName(providerInternalApiClass),
            getBaseContinuationAccessorMethodName,
            "()${Type.getDescriptor(BaseContinuationAccessor::class.java)}"
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
            Type.getInternalName(providerInternalApiClass),
            prepareBaseContinuationAccessorMethodName,
            "(${Type.getDescriptor(MethodHandles.Lookup::class.java)})${Type.getDescriptor(BaseContinuationAccessor::class.java)}"
        ))
        add(decoroutinatorAwakeLabel)
        add(FrameNode(Opcodes.F_SAME1, 0, null, 1, arrayOf(Type.getInternalName(Object::class.java))))
        add(VarInsnNode(Opcodes.ALOAD, 0))
        add(VarInsnNode(Opcodes.ALOAD, 1))
        add(MethodInsnNode(
            Opcodes.INVOKESTATIC,
            Type.getInternalName(providerInternalApiClass),
            awakeBaseContinuationMethodName,
            "(${Type.getDescriptor(BaseContinuationAccessor::class.java)}${Type.getDescriptor(Object::class.java)}${Type.getDescriptor(Object::class.java)})${Type.VOID_TYPE.descriptor}"
        ))
        add(InsnNode(Opcodes.RETURN))
        add(defaultAwakeLabel)
        add(FrameNode(Opcodes.F_SAME, 0, null, 0, null))
    })

    val getStackTraceElementMethod = methods?.find {
        it.desc == "()${Type.getDescriptor(StackTraceElement::class.java)}" && !it.isStatic
    } ?: error("[${BaseContinuation::getStackTraceElement.name}] method is not found")
    getStackTraceElementMethod.instructions.insertBefore(
        getStackTraceElementMethod.instructions.first,
        InsnList().apply {
            add(MethodInsnNode(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(providerInternalApiClass),
                isUsingElementFactoryForBaseContinuationEnabledMethodName,
                "()${Type.BOOLEAN_TYPE.descriptor}"
            ))
            val defaultLabel = LabelNode()
            add(JumpInsnNode(
                Opcodes.IFEQ,
                defaultLabel
            ))
            add(VarInsnNode(Opcodes.ALOAD, 0))
            add(MethodInsnNode(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(providerInternalApiClass),
                getElementFactoryStacktraceElementMethodName,
                "(${Type.getDescriptor(Object::class.java)})${Type.getDescriptor(StackTraceElement::class.java)}"
            ))
            add(InsnNode(Opcodes.ARETURN))
            add(defaultLabel)
            add(FrameNode(Opcodes.F_SAME, 0, null, 0, null))
        }
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

private fun ClassNode.generateSpecMethodsAndTransformAnnotation(
    skipSpecMethods: Boolean,
    lineNumbersBySpecMethodName: Map<String, Set<Int>>
) {
    val isInterface = access and Opcodes.ACC_INTERFACE != 0
    val isPrivateMethodsInInterfacesSupported = version >= Opcodes.V9
    val makePrivate = !isInterface || isPrivateMethodsInInterfacesSupported
    val makeFinal = !isInterface
    version = maxOf(version, Opcodes.V1_7)
    if (!skipSpecMethods) {
        lineNumbersBySpecMethodName.forEach { (methodName, lineNumbers) ->
            methods.add(
                buildSpecMethodNode(
                    methodName = methodName,
                    lineNumbers = lineNumbers,
                    makePrivate = makePrivate,
                    makeFinal = makeFinal
                )
            )
        }
    }

    if (lineNumbersBySpecMethodName.isNotEmpty()) {
        val clinit = getOrCreateClinitMethod()
        clinit.instructions.insertBefore(
            clinit.instructions.first,
            buildCallRegisterLookupInstructions()
        )
    }

    visibleAnnotations = visibleAnnotations.orEmpty() +
        AnnotationNode(Opcodes.ASM9, Type.getDescriptor(DecoroutinatorTransformed::class.java)).apply {
            val lineNumbers = lineNumbersBySpecMethodName.entries.toList()
            values = buildList {
                if (sourceFile != null) {
                    add(DecoroutinatorTransformed::fileName.name)
                    add(sourceFile)
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

                if (skipSpecMethods) {
                    add(DecoroutinatorTransformed::skipSpecMethods.name)
                    add(true)
                }
            }
        }
}

private val ClassNode.kotlinMetadataAnnotation: AnnotationNode?
    get() = visibleAnnotations
        .orEmpty()
        .firstOrNull { it.desc == Type.getDescriptor(Metadata::class.java) }

private fun ClassNode.tryTransformSuspendMethods(
    metadataResolver: (className: String) -> DebugMetadataInfo?,
    lineNumbersBySpecMethodName: MutableMap<String, MutableSet<Int>>,
    notSuspendFunctionSignatures: Collection<JvmMethodSignature>
): Boolean {
    var needTransformation = false
    val check = { info: DebugMetadataInfo? ->
        if (info != null && info.specClassInternalClassName == name) {
            needTransformation = true
            val currentLineNumbers = lineNumbersBySpecMethodName.computeIfAbsent(info.methodName) {
                hashSetOf(UNKNOWN_LINE_NUMBER)
            }
            currentLineNumbers.addAll(info.lineNumbers)
        }
    }
    check(debugMetadataInfo)
    methods.orEmpty().forEach { method ->
        when (val status = getCheckTransformationStatus(this, method, notSuspendFunctionSignatures)) {
            is DefaultTransformationStatus -> check(metadataResolver(status.baseContinuationClassName))
            is TailCallTransformationStatus -> {
                if (tailCallDeopt(
                    completionVarIndex = status.completionVarIndex,
                    clazz = this,
                    method = method,
                    lineNumbersBySpecMethodName = lineNumbersBySpecMethodName
                )) {
                    needTransformation = true
                }
            }
            null -> { }
        }
    }
    return needTransformation
}

private val getBaseContinuationMethodName: String
    @LoadConstant("getBaseContinuationMethodName") get() = fail()

private fun tailCallDeopt(
    completionVarIndex: Int,
    clazz: ClassNode,
    method: MethodNode,
    lineNumbersBySpecMethodName: MutableMap<String, MutableSet<Int>>
): Boolean {
    var result = false
    method.instructions.forEach { instruction ->
        if (instruction is VarInsnNode && instruction.opcode == Opcodes.ALOAD && instruction.`var` == completionVarIndex) {
            result = true
            val lineNumber = generateSequence(instruction.previous, { it.previous })
                .mapNotNull { it as? LineNumberNode }
                .firstOrNull()?.line ?: generateSequence(instruction.next, { it.next })
                .takeWhile { it.opcode == -1 }
                .mapNotNull { it as? LineNumberNode }
                .firstOrNull()?.line ?: UNKNOWN_LINE_NUMBER
            val currentLineNumbers = lineNumbersBySpecMethodName.computeIfAbsent(method.name) {
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
                    getBaseContinuationMethodName,
                    "("
                        + Type.getDescriptor(Object::class.java)
                        + Type.getDescriptor(String::class.java)
                        + Type.getDescriptor(String::class.java)
                        + Type.getDescriptor(String::class.java)
                        + Type.INT_TYPE.descriptor
                        + ")${Type.getDescriptor(Object::class.java)}"
                ))
                add(TypeInsnNode(Opcodes.CHECKCAST, Type.getInternalName(Continuation::class.java)))
            })
        }
    }
    return result
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

private fun getCheckTransformationStatus(
    clazz: ClassNode,
    method: MethodNode,
    notSuspendFunctionSignatures: Collection<JvmMethodSignature>
): CheckTransformationStatus? {
    if (
        method.name == "<init>"
        || method.name == "<clinit>"
        || !method.desc.endsWith("${Type.getDescriptor(Continuation::class.java)})${Type.getDescriptor(Object::class.java)}")
        || method.instructions == null
        || method.instructions.size() == 0
        || notSuspendFunctionSignatures.any { it.name == method.name && it.descriptor == method.desc }
    ) return null
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
        val isInterface = clazz.access and Opcodes.ACC_INTERFACE != 0
        val isMethodStatic = method.access and Opcodes.ACC_STATIC != 0
        if (
            hasCompletionLocalVar &&
            !hasContinuationLocalVar && (
                    !isMethodSynthetic || (isInterface && isMethodStatic && method.name.startsWith("defaultImpl\$"))
                    )
        ) {
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

private val registerTransformedClassMethodName: String
    @LoadConstant("registerTransformedClassMethodName") get() = fail()

private fun buildCallRegisterLookupInstructions() = InsnList().apply {
    add(MethodInsnNode(
        Opcodes.INVOKESTATIC,
        Type.getInternalName(providerApiClass),
        isDecoroutinatorEnabledMethodName,
        "()${Type.BOOLEAN_TYPE.descriptor}"
    ))
    val disabledLabel = LabelNode()
    add(JumpInsnNode(
        Opcodes.IFEQ,
        disabledLabel
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
        registerTransformedClassMethodName,
        "(${Type.getDescriptor(MethodHandles.Lookup::class.java)})V"
    ))
    add(disabledLabel)
    add(FrameNode(Opcodes.F_SAME, 0, null, 0, null))
}

private val ClassNode.classBody: ByteArray
    get() {
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        accept(writer)
        return writer.toByteArray()
    }
