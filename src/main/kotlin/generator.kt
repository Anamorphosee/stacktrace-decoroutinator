package dev.reformator.stacktracedecoroutinator.generator

import dev.reformator.stacktracedecoroutinator.analyzer.DecoroutinatorClassSpec
import dev.reformator.stacktracedecoroutinator.util.pairComparator
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*

typealias DecoroutinatorClassBodyGenerator = (className: String, spec: DecoroutinatorClassSpec) -> ByteArray

class DecoroutinatorClassBodyGeneratorImpl: DecoroutinatorClassBodyGenerator {
    companion object {
        val STACK_METHOD_HANDLERS_VAR_INDEX = 0
        val LINE_NUMBERS_VAR_INDEX = 1
        val NEXT_STEP_VAR_INDEX = 2
        val CONTINUATION_METHOD_HANDLERS_FUNCTION_VAR_INDEX = 3
        val RESULT_VAR_INDEX = 4
        val SUSPEND_OBJECT_VAR_INDEX = 5
        val LINE_NUMBER_VAR_INDEX = 6

        val METHOD_HANDLE_INTERNAL_CLASS_NAME = "java/lang/invoke/MethodHandle"
        val FUNCTION_INTERNAL_CLASS_NAME = "java/util/function/Function"
        val OBJECT_INTERNAL_CLASS_NAME = "java/lang/Object"
        val THROWABLE_INTERNAL_CLASS_NAME = "java/lang/Throwable"
        val INTEGER_INTERNAL_CLASS_NAME = Type.getType(Integer::class.java).internalName
        val STRING_BUILDER_INTERNAL_CLASS_NAME = Type.getType(java.lang.StringBuilder::class.java).internalName
        val STRING_INTERNAL_CLASS_NAME = Type.getType(java.lang.String::class.java).internalName

        //MethodHandle[] stackMethodHandlers
        //int[] stackLineNumbers
        //int nextStep
        //Function<Integer, MethodHandle> continuationInvokeMethods
        //Object result
        //Object suspendObject
        val METHOD_DESC = "(" +
                "[L$METHOD_HANDLE_INTERNAL_CLASS_NAME;" +
                "[I" +
                "I" +
                "L$FUNCTION_INTERNAL_CLASS_NAME;" +
                "L$OBJECT_INTERNAL_CLASS_NAME;" +
                "L$OBJECT_INTERNAL_CLASS_NAME;" +
                ")L$OBJECT_INTERNAL_CLASS_NAME;"
    }

    override fun invoke(className: String, spec: DecoroutinatorClassSpec): ByteArray {
        val classNode = ClassNode().apply {
            version = Opcodes.V1_8
            access = Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER
            name = className.replace('.', '/')
            superName = OBJECT_INTERNAL_CLASS_NAME
            sourceFile = spec.sourceFileName
        }

        val methodName2LineNumbers = spec.continuationClassName2Method.values.asSequence()
            .flatMap { method ->
                method.label2LineNumber.values.asSequence().map { method.methodName to it }
            }
            .sortedWith(pairComparator())
            .distinct()
            .groupBy({ it.first }) { it.second }

        classNode.methods = methodName2LineNumbers.entries.map { (methodName, lineNumbers) ->
            val methodNode = MethodNode().apply {
                access = Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL
                name = methodName
                desc = METHOD_DESC
            }

            val beforeInvokeNextStacktraceMethodLabel = LabelNode()
            val afterInvokeNextStacktraceMethodLabel = LabelNode()
            val exceptionInInvokeNextStacktraceMethodLabel = LabelNode()
            methodNode.instructions.apply {
                add(getStoreLineNumberInstructions())

                val invokeContinuationLabel = LabelNode()
                add(getGotoIfLastStepInstructions(invokeContinuationLabel))

                val invalidLineNumberLabel = LabelNode()
                add(beforeInvokeNextStacktraceMethodLabel)
                add(getInvokeNextStacktraceMethodInstructions(invalidLineNumberLabel, lineNumbers))
                add(afterInvokeNextStacktraceMethodLabel)

                add(getReturnSuspendObjectIfResultIsSuspendObjectInstructions())

                add(invokeContinuationLabel)
                add(getInvokeContinuationAndReturnInstructions(invalidLineNumberLabel, lineNumbers))

                add(invalidLineNumberLabel)
                add(getThrowInvalidLineNumberInstructions())

                add(exceptionInInvokeNextStacktraceMethodLabel)
                add(getStoreExceptionResultInstructions())
                add(getGotoInstruction(invokeContinuationLabel))
            }

            methodNode.tryCatchBlocks = listOf(TryCatchBlockNode(
                beforeInvokeNextStacktraceMethodLabel,
                afterInvokeNextStacktraceMethodLabel,
                exceptionInInvokeNextStacktraceMethodLabel,
                THROWABLE_INTERNAL_CLASS_NAME
            ))

            methodNode
        }

        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        classNode.accept(writer)
        return writer.toByteArray()
    }

    private fun getStoreLineNumberInstructions() = InsnList().apply {
        add(VarInsnNode(Opcodes.ALOAD, LINE_NUMBERS_VAR_INDEX))
        add(VarInsnNode(Opcodes.ILOAD, NEXT_STEP_VAR_INDEX))
        add(InsnNode(Opcodes.ICONST_1))
        add(InsnNode(Opcodes.ISUB))
        add(InsnNode(Opcodes.IALOAD))
        add(VarInsnNode(Opcodes.ISTORE, LINE_NUMBER_VAR_INDEX))
    }

    private fun getGotoIfLastStepInstructions(label: LabelNode) = InsnList().apply {
        add(VarInsnNode(Opcodes.ALOAD, STACK_METHOD_HANDLERS_VAR_INDEX))
        add(InsnNode(Opcodes.ARRAYLENGTH))
        add(VarInsnNode(Opcodes.ILOAD, NEXT_STEP_VAR_INDEX))
        add(JumpInsnNode(Opcodes.IF_ICMPEQ, label))
    }

    private fun getInvokeNextStacktraceMethodInstructions(
        invalidLineNumberLabel: LabelNode, lineNumbers: List<UInt>
    ) = InsnList().apply {
        add(VarInsnNode(Opcodes.ALOAD, STACK_METHOD_HANDLERS_VAR_INDEX))
        add(VarInsnNode(Opcodes.ILOAD, NEXT_STEP_VAR_INDEX))
        add(InsnNode(Opcodes.AALOAD))
        add(VarInsnNode(Opcodes.ALOAD, STACK_METHOD_HANDLERS_VAR_INDEX))
        add(VarInsnNode(Opcodes.ALOAD, LINE_NUMBERS_VAR_INDEX))
        add(VarInsnNode(Opcodes.ILOAD, NEXT_STEP_VAR_INDEX))
        add(InsnNode(Opcodes.ICONST_1))
        add(InsnNode(Opcodes.IADD))
        add(VarInsnNode(Opcodes.ALOAD, CONTINUATION_METHOD_HANDLERS_FUNCTION_VAR_INDEX))
        add(VarInsnNode(Opcodes.ALOAD, RESULT_VAR_INDEX))
        add(VarInsnNode(Opcodes.ALOAD, SUSPEND_OBJECT_VAR_INDEX))
        val invalidLabel = LabelNode()
        val endLabel = LabelNode()
        addByLineNumbers(invalidLabel, lineNumbers, false) {
            add(MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, METHOD_HANDLE_INTERNAL_CLASS_NAME, "invokeExact",
                METHOD_DESC
            ))
            add(getGotoInstruction(endLabel))
        }
        add(invalidLabel)
        add(InsnNode(Opcodes.POP2))
        add(InsnNode(Opcodes.POP2))
        add(InsnNode(Opcodes.POP2))
        add(InsnNode(Opcodes.POP))
        add(getGotoInstruction(invalidLineNumberLabel))
        add(endLabel)
        add(VarInsnNode(Opcodes.ASTORE, RESULT_VAR_INDEX))
    }

    private fun InsnList.addByLineNumbers(
            invalidLineNumberLabel: LabelNode,
            lineNumbers: List<UInt>,
            appendGotoEnd: Boolean = true,
            action: InsnList.(lineNumber: UInt) -> Unit
    ) {
        val array = lineNumbers.toUIntArray()
        val labels = Array(lineNumbers.size) { LabelNode() }
        val endLabel = if (appendGotoEnd) LabelNode() else null
        add(VarInsnNode(Opcodes.ILOAD, LINE_NUMBER_VAR_INDEX))
        add(LookupSwitchInsnNode(invalidLineNumberLabel, array.asIntArray(), labels))
        array.forEachIndexed { index, lineNumber ->
            val label = labels[index]
            add(label)
            add(LineNumberNode(lineNumber.toInt(), label))
            action(lineNumber)
            if (endLabel != null && index < array.lastIndex) {
                add(getGotoInstruction(endLabel))
            }
        }
        if (endLabel != null) {
            add(endLabel)
        }
    }

    private fun getStoreExceptionResultInstructions() = InsnList().apply {
        add(MethodInsnNode(Opcodes.INVOKESTATIC, "kotlin/ResultKt", "createFailure",
            "(L$THROWABLE_INTERNAL_CLASS_NAME;)L$OBJECT_INTERNAL_CLASS_NAME;"))
        add(VarInsnNode(Opcodes.ASTORE, RESULT_VAR_INDEX))
    }

    private fun getGotoInstruction(label: LabelNode) = JumpInsnNode(Opcodes.GOTO, label)

    private fun getThrowInvalidLineNumberInstructions() = InsnList().apply {
        add(TypeInsnNode(Opcodes.NEW, "java/lang/IllegalArgumentException"))
        add(InsnNode(Opcodes.DUP))
        add(TypeInsnNode(Opcodes.NEW, STRING_BUILDER_INTERNAL_CLASS_NAME))
        add(InsnNode(Opcodes.DUP))
        add(LdcInsnNode("invalid line number: "))
        add(MethodInsnNode(Opcodes.INVOKESPECIAL, STRING_BUILDER_INTERNAL_CLASS_NAME, "<init>",
            "(L$STRING_INTERNAL_CLASS_NAME;)V"))
        add(VarInsnNode(Opcodes.ILOAD, LINE_NUMBER_VAR_INDEX))
        add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, STRING_BUILDER_INTERNAL_CLASS_NAME, "append",
            "(I)L$STRING_BUILDER_INTERNAL_CLASS_NAME;"))
        add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, STRING_BUILDER_INTERNAL_CLASS_NAME, "toString",
            "()L$STRING_INTERNAL_CLASS_NAME;"))
        add(MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>",
            "(L$STRING_INTERNAL_CLASS_NAME;)V"))
        add(InsnNode(Opcodes.ATHROW))
    }

    private fun getReturnSuspendObjectIfResultIsSuspendObjectInstructions() = InsnList().apply {
        add(VarInsnNode(Opcodes.ALOAD, RESULT_VAR_INDEX))
        add(VarInsnNode(Opcodes.ALOAD, SUSPEND_OBJECT_VAR_INDEX))
        val endLabel = LabelNode()
        add(JumpInsnNode(Opcodes.IF_ACMPNE, endLabel))
        add(VarInsnNode(Opcodes.ALOAD, SUSPEND_OBJECT_VAR_INDEX))
        add(InsnNode(Opcodes.ARETURN))
        add(endLabel)
    }

    private fun getInvokeContinuationAndReturnInstructions(
        invalidLineNumberLabel: LabelNode, lineNumbers: List<UInt>
    ) = InsnList().apply {
        add(VarInsnNode(Opcodes.ALOAD, CONTINUATION_METHOD_HANDLERS_FUNCTION_VAR_INDEX))
        add(VarInsnNode(Opcodes.ILOAD, NEXT_STEP_VAR_INDEX))
        add(InsnNode(Opcodes.ICONST_1))
        add(InsnNode(Opcodes.ISUB))
        add(MethodInsnNode(Opcodes.INVOKESTATIC, INTEGER_INTERNAL_CLASS_NAME, "valueOf",
            "(I)L$INTEGER_INTERNAL_CLASS_NAME;"))
        add(MethodInsnNode(Opcodes.INVOKEINTERFACE, FUNCTION_INTERNAL_CLASS_NAME, "apply",
            "(L$OBJECT_INTERNAL_CLASS_NAME;)L$OBJECT_INTERNAL_CLASS_NAME;"))
        add(TypeInsnNode(Opcodes.CHECKCAST, METHOD_HANDLE_INTERNAL_CLASS_NAME))
        add(VarInsnNode(Opcodes.ALOAD, RESULT_VAR_INDEX))
        val invalidLabel = LabelNode()
        addByLineNumbers(invalidLabel, lineNumbers) {
            add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, METHOD_HANDLE_INTERNAL_CLASS_NAME, "invokeExact",
                "(L$OBJECT_INTERNAL_CLASS_NAME;)L$OBJECT_INTERNAL_CLASS_NAME;"))
        }
        add(InsnNode(Opcodes.ARETURN))
        add(invalidLabel)
        add(InsnNode(Opcodes.POP2))
        add(getGotoInstruction(invalidLineNumberLabel))
    }
}
