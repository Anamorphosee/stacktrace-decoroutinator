package dev.reformator.stacktracedecoroutinator.analyzer

import dev.reformator.stacktracedecoroutinator.util.OBJECT_INTERNAL_CLASS_NAME
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import java.nio.file.FileSystems
import kotlin.IllegalArgumentException

data class DecoroutinatorMethodSpec(
    val methodName: String,
    val label2LineNumber: Map<Int, Int>
)

data class DecoroutinatorClassSpec(
    val sourceFileName: String?,
    val continuationClassName2Method: Map<String, DecoroutinatorMethodSpec>
)

interface ClassBodyResolver {
    fun getClassBodies(className: String): List<ByteArray>

    fun getClassBody(className: String) = getClassBodies(className).let {
        if (it.size > 1) {
            throw IllegalArgumentException("found ${it.size} class bodies for class $className")
        }
        it.singleOrNull()
    }
}

interface DecoroutinatorClassAnalyzer {
    fun getDecoroutinatorClassSpec(className: String): DecoroutinatorClassSpec
    fun getClassNameByContinuationClassName(coroutineClassName: String): String
}

class DefaultClassBodyResolver: ClassBodyResolver {
    companion object {
        private val regex = Regex.fromLiteral(".")
        private val separator: String = FileSystems.getDefault().separator
    }

    override fun getClassBodies(className: String): List<ByteArray> {
        val path = className.replace(regex, separator) + ".class"
        val enumerator = ClassLoader.getSystemResources(path)
        return buildList {
            while (enumerator.hasMoreElements()) {
                add(enumerator.nextElement().openStream().readBytes())
            }
        }
    }
}

class DecoroutinatorClassAnalyzerImpl(
    private val classBodyResolver: ClassBodyResolver
): DecoroutinatorClassAnalyzer {
    companion object {
        private val intTypeDescriptor = Type.INT_TYPE.descriptor
        private val baseContinuationSuperClassesInternalNames = setOf(
            "kotlin/coroutines/jvm/internal/BaseContinuationImpl",
            "kotlin/coroutines/jvm/internal/ContinuationImpl",
            "kotlin/coroutines/jvm/internal/RestrictedContinuationImpl",
            "kotlin/coroutines/jvm/internal/RestrictedSuspendLambda",
            "kotlin/coroutines/jvm/internal/SuspendLambda",
            "kotlinx/coroutines/flow/internal/SafeCollector"
        )
    }

    constructor(): this(DefaultClassBodyResolver())

    override fun getDecoroutinatorClassSpec(className: String): DecoroutinatorClassSpec {
        val classNode = getClassNode(className) ?: throw IllegalArgumentException("class $className is not found")
        val continuationClassName2Method = classNode.methods.asSequence()
            .mapNotNull { methodNode ->
                val continuationInternalClassName = getContinuationInternalClassName(classNode, methodNode) ?:
                        return@mapNotNull null
                val lineNumbers = getLineNumbers(methodNode.instructions)
                val label2LineNumber = mutableMapOf<Int, Int>()
                for (instruction in methodNode.instructions) {
                    if (instruction !is FieldInsnNode || instruction.opcode != Opcodes.PUTFIELD ||
                        instruction.owner != continuationInternalClassName || instruction.name != "label" ||
                        instruction.desc != intTypeDescriptor) {
                        continue
                    }
                    val previousInstruction = getPreviousInstruction(instruction)
                    if (previousInstruction?.opcode == Opcodes.ISUB) {
                        continue
                    }
                    val label = getPreviousInstruction(instruction)?.let {
                        checkPushConstantIntInstruction(it)
                    } ?: return@mapNotNull null
                    val lineNumber = lineNumbers[instruction]
                    label2LineNumber[label] = lineNumber ?: 1
                }
                val continuationClassName = continuationInternalClassName.replace('/', '.')
                continuationClassName to DecoroutinatorMethodSpec(methodNode.name, label2LineNumber)
            }
            .toMap()
        return DecoroutinatorClassSpec(classNode.sourceFile, continuationClassName2Method)
    }

    override fun getClassNameByContinuationClassName(coroutineClassName: String): String {
        val classNode = getClassNode(coroutineClassName) ?:
                throw IllegalArgumentException("class $coroutineClassName is not found")
        val resumeMethod = classNode.methods.asSequence()
            .filter { it.name == "invokeSuspend" && it.desc == "(Ljava/lang/Object;)Ljava/lang/Object;" }
            .first()
        val invokeSuspendedMethodInstruction = run {
            var candidate = resumeMethod.instructions.last
            while (candidate !is MethodInsnNode) {
                candidate = candidate.previous
            }
            candidate
        }
        return invokeSuspendedMethodInstruction.owner.let {
            if (it == "java/lang/IllegalStateException") {
                coroutineClassName
            } else {
                it.replace('/', '.')
            }
        }
    }

    private fun getClassNode(className: String): ClassNode? {
        val classBody = classBodyResolver.getClassBody(className) ?: return null
        val classNode = ClassNode()
        val classReader = ClassReader(classBody)
        classReader.accept(classNode, ClassReader.SKIP_FRAMES)
        return classNode
    }

    private fun getContinuationVarIndex(methodNode: MethodNode): UInt {
        val static = methodNode.access and Opcodes.ACC_STATIC != 0
        val argumentsSizeWithThis = (Type.getArgumentsAndReturnSizes(methodNode.desc) ushr 2).toUInt()
        return argumentsSizeWithThis - (if (static) 2U else 1U)
    }

    private fun getContinuationInternalClassName(classNode: ClassNode, methodNode: MethodNode): String? {
        return if (
            methodNode.access and Opcodes.ACC_STATIC == 0 &&
                methodNode.name == "invokeSuspend" &&
                methodNode.desc == "(L$OBJECT_INTERNAL_CLASS_NAME;)L$OBJECT_INTERNAL_CLASS_NAME;" &&
                classNode.superName in baseContinuationSuperClassesInternalNames) {
            classNode.name
        } else if (
            methodNode.desc.endsWith("Lkotlin/coroutines/Continuation;)Ljava/lang/Object;")
        ) {
            val continuationVarIndex = getContinuationVarIndex(methodNode)
            methodNode.instructions[0].also {
                if (it !is VarInsnNode) {
                    return null
                }
                if (it.`var`.toUInt() != continuationVarIndex) {
                    return null
                }
                if (it.opcode != Opcodes.ALOAD) {
                    return null
                }
            }
            methodNode.instructions[1].let {
                if (it !is TypeInsnNode) {
                    return null
                }
                if (it.opcode != Opcodes.INSTANCEOF) {
                    return null
                }
                it.desc
            }
        } else {
            null
        }
    }

    private fun getPreviousInstruction(instruction: AbstractInsnNode): AbstractInsnNode? {
        var candidate: AbstractInsnNode? = instruction.previous
        while (candidate != null && candidate.opcode == -1 && candidate.opcode == Opcodes.NOP) {
            candidate = candidate.previous
        }
        return candidate
    }

    private fun checkPushConstantIntInstruction(instruction: AbstractInsnNode): Int? =
        when (instruction) {
            is InsnNode -> when (instruction.opcode) {
                Opcodes.ICONST_M1 -> -1
                Opcodes.ICONST_0 -> 0
                Opcodes.ICONST_1 -> 1
                Opcodes.ICONST_2 -> 2
                Opcodes.ICONST_3 -> 3
                Opcodes.ICONST_4 -> 4
                Opcodes.ICONST_5 -> 5
                else -> null
            }
            is IntInsnNode -> when (instruction.opcode) {
                Opcodes.BIPUSH, Opcodes.SIPUSH -> instruction.operand
                else -> null
            }
            is LdcInsnNode -> instruction.cst as? Int
            else -> null
        }

    private fun getLineNumbers(instructions: InsnList): Map<AbstractInsnNode, Int> {
        val labelNode2LineNumber = instructions.asSequence()
            .mapNotNull { it as? LineNumberNode }
            .map { it.start!! to it.line }
            .toMap()
        var currentLineNumber: Int? = null
        return instructions.asSequence()
            .mapNotNull { instruction: AbstractInsnNode ->
                if (instruction is LabelNode) {
                    labelNode2LineNumber[instruction]?.let { currentLineNumber = it }
                }
                currentLineNumber?.let { instruction to it }
            }.toMap()
    }
}
