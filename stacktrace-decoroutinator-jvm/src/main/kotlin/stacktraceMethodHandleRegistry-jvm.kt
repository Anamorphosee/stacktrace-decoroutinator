package dev.reformator.stacktracedecoroutinator.registry

import dev.reformator.stacktracedecoroutinator.generator.DecoroutinatorClassLoader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import java.lang.invoke.MethodHandle
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.BiFunction


object DecoroutinatorStacktraceMethodHandleRegistryImpl: BaseDecoroutinatorStacktraceMethodHandleRegistry() {
    private const val STACK_METHOD_HANDLERS_VAR_INDEX = 0
    private const val LINE_NUMBERS_VAR_INDEX = 1
    private const val NEXT_STEP_VAR_INDEX = 2
    private const val INVOKE_FUNCTION_VAR_INDEX = 3
    private const val RESULT_VAR_INDEX = 4
    private const val SUSPEND_OBJECT_VAR_INDEX = 5
    private const val LINE_NUMBER_VAR_INDEX = 6

    private val METHOD_HANDLE_INTERNAL_CLASS_NAME = Type.getInternalName(MethodHandle::class.java)
    private val BI_FUNCTION_INTERNAL_CLASS_NAME = Type.getInternalName(BiFunction::class.java)
    private val OBJECT_INTERNAL_CLASS_NAME = Type.getInternalName(Object::class.java)
    private val INTEGER_INTERNAL_CLASS_NAME = Type.getInternalName(Integer::class.java)
    private val STRING_BUILDER_INTERNAL_CLASS_NAME = Type.getInternalName(java.lang.StringBuilder::class.java)
    private val STRING_INTERNAL_CLASS_NAME = Type.getInternalName(java.lang.String::class.java)

    //MethodHandle[] stackMethodHandlers
    //int[] stackLineNumbers
    //int nextStep
    //BiFunction<Integer, Object, Object> continuationInvokeMethods
    //Object result
    //Object suspendObject
    private val METHOD_DESC = "(" +
            "[L$METHOD_HANDLE_INTERNAL_CLASS_NAME;" +
            "[I" +
            "I" +
            "L$BI_FUNCTION_INTERNAL_CLASS_NAME;" +
            "L$OBJECT_INTERNAL_CLASS_NAME;" +
            "L$OBJECT_INTERNAL_CLASS_NAME;" +
            ")L$OBJECT_INTERNAL_CLASS_NAME;"

    private val classLoaderByRevision: MutableList<DecoroutinatorClassLoader> = CopyOnWriteArrayList()

    override fun generateStacktraceClass(
        className: String,
        fileName: String?,
        classRevision: Int,
        methodName2LineNumbers: Map<String, Set<Int>>
    ): Class<*> {
        val classBody = getClassBody(className, fileName, methodName2LineNumbers)

        while (classLoaderByRevision.size <= classRevision) {
            classLoaderByRevision.add(DecoroutinatorClassLoader())
        }

        return classLoaderByRevision[classRevision].defineClass(className, classBody)
    }

    private fun getClassBody(
        className: String,
        fileName: String?,
        methodName2LineNumbers: Map<String, Set<Int>>
    ): ByteArray {
        val classNode = ClassNode(Opcodes.ASM9).apply {
            version = Opcodes.V1_8
            access = Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER
            name = className.replace('.', '/')
            superName = OBJECT_INTERNAL_CLASS_NAME
            sourceFile = fileName
        }
        classNode.methods = methodName2LineNumbers.entries.map { (methodName, lineNumbers) ->
            val methodNode = MethodNode().apply {
                access = Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL
                name = methodName
                desc = METHOD_DESC
            }
            val sortedLineNumbers = lineNumbers.sorted()
            methodNode.instructions.apply {
                add(getStoreLineNumberInstructions())

                val invokeFunctionLabel = LabelNode()
                add(getGotoIfLastStepInstructions(invokeFunctionLabel))

                val invalidLineNumberLabel = LabelNode()
                add(getInvokeNextStacktraceMethodInstructions(invalidLineNumberLabel, sortedLineNumbers))

                add(getReturnSuspendObjectIfResultIsSuspendObjectInstructions())

                add(invokeFunctionLabel)
                add(getInvokeFunctionAndReturnInstructions(invalidLineNumberLabel, sortedLineNumbers))

                add(invalidLineNumberLabel)
                add(getThrowInvalidLineNumberInstructions())
            }
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
        invalidLineNumberLabel: LabelNode, lineNumbers: List<Int>
    ) = InsnList().apply {
        add(VarInsnNode(Opcodes.ALOAD, STACK_METHOD_HANDLERS_VAR_INDEX))
        add(VarInsnNode(Opcodes.ILOAD, NEXT_STEP_VAR_INDEX))
        add(InsnNode(Opcodes.AALOAD))
        add(VarInsnNode(Opcodes.ALOAD, STACK_METHOD_HANDLERS_VAR_INDEX))
        add(VarInsnNode(Opcodes.ALOAD, LINE_NUMBERS_VAR_INDEX))
        add(VarInsnNode(Opcodes.ILOAD, NEXT_STEP_VAR_INDEX))
        add(InsnNode(Opcodes.ICONST_1))
        add(InsnNode(Opcodes.IADD))
        add(VarInsnNode(Opcodes.ALOAD, INVOKE_FUNCTION_VAR_INDEX))
        add(VarInsnNode(Opcodes.ALOAD, RESULT_VAR_INDEX))
        add(VarInsnNode(Opcodes.ALOAD, SUSPEND_OBJECT_VAR_INDEX))
        val invalidLabel = LabelNode()
        val endLabel = LabelNode()
        addByLineNumbers(invalidLabel, lineNumbers, false) {
            add(
                MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL, METHOD_HANDLE_INTERNAL_CLASS_NAME, "invokeExact",
                    METHOD_DESC
                )
            )
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
        lineNumbers: List<Int>,
        appendGotoEnd: Boolean = true,
        action: InsnList.(lineNumber: Int) -> Unit
    ) {
        val array = lineNumbers.toIntArray()
        val labels = Array(lineNumbers.size) { LabelNode() }
        val endLabel = if (appendGotoEnd) LabelNode() else null
        add(VarInsnNode(Opcodes.ILOAD, LINE_NUMBER_VAR_INDEX))
        add(LookupSwitchInsnNode(invalidLineNumberLabel, array, labels))
        array.forEachIndexed { index, lineNumber ->
            val label = labels[index]
            add(label)
            add(LineNumberNode(lineNumber, label))
            action(lineNumber)
            if (endLabel != null && index < array.lastIndex) {
                add(getGotoInstruction(endLabel))
            }
        }
        if (endLabel != null) {
            add(endLabel)
        }
    }

    private fun getGotoInstruction(label: LabelNode) = JumpInsnNode(Opcodes.GOTO, label)

    private fun getThrowInvalidLineNumberInstructions() = InsnList().apply {
        add(TypeInsnNode(Opcodes.NEW, "java/lang/IllegalArgumentException"))
        add(InsnNode(Opcodes.DUP))
        add(TypeInsnNode(Opcodes.NEW, STRING_BUILDER_INTERNAL_CLASS_NAME))
        add(InsnNode(Opcodes.DUP))
        add(LdcInsnNode("invalid line number: "))
        add(
            MethodInsnNode(
                Opcodes.INVOKESPECIAL, STRING_BUILDER_INTERNAL_CLASS_NAME, "<init>",
                "(L$STRING_INTERNAL_CLASS_NAME;)V")
        )
        add(VarInsnNode(Opcodes.ILOAD, LINE_NUMBER_VAR_INDEX))
        add(
            MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, STRING_BUILDER_INTERNAL_CLASS_NAME, "append",
                "(I)L$STRING_BUILDER_INTERNAL_CLASS_NAME;")
        )
        add(
            MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, STRING_BUILDER_INTERNAL_CLASS_NAME, "toString",
                "()L$STRING_INTERNAL_CLASS_NAME;")
        )
        add(
            MethodInsnNode(
                Opcodes.INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>",
                "(L$STRING_INTERNAL_CLASS_NAME;)V")
        )
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

    private fun getInvokeFunctionAndReturnInstructions(
        invalidLineNumberLabel: LabelNode, lineNumbers: List<Int>
    ) = InsnList().apply {
        add(VarInsnNode(Opcodes.ALOAD, INVOKE_FUNCTION_VAR_INDEX))
        add(VarInsnNode(Opcodes.ILOAD, NEXT_STEP_VAR_INDEX))
        add(
            MethodInsnNode(
                Opcodes.INVOKESTATIC, INTEGER_INTERNAL_CLASS_NAME, "valueOf",
                "(I)L$INTEGER_INTERNAL_CLASS_NAME;")
        )
        add(VarInsnNode(Opcodes.ALOAD, RESULT_VAR_INDEX))
        val invalidLabel = LabelNode()
        addByLineNumbers(invalidLabel, lineNumbers) {
            add(
                MethodInsnNode(
                    Opcodes.INVOKEINTERFACE, BI_FUNCTION_INTERNAL_CLASS_NAME, "apply",
                    "(L$OBJECT_INTERNAL_CLASS_NAME;L$OBJECT_INTERNAL_CLASS_NAME;)L$OBJECT_INTERNAL_CLASS_NAME;")
            )
        }
        add(InsnNode(Opcodes.ARETURN))
        add(invalidLabel)
        add(InsnNode(Opcodes.POP2))
        add(InsnNode(Opcodes.POP))
        add(getGotoInstruction(invalidLineNumberLabel))
    }
}
