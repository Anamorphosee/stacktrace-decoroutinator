@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.generator.internal

import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import java.lang.invoke.MethodHandle

internal fun buildSpecMethodNode(methodName: String, lineNumbers: Set<Int>, makePrivate: Boolean): MethodNode {
    val result = MethodNode(Opcodes.ASM9).apply {
        access = if (makePrivate) Opcodes.ACC_PRIVATE else Opcodes.ACC_PUBLIC
        access = access or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL or Opcodes.ACC_SYNTHETIC
        name = methodName
        desc = "(L$SPEC_INTERNAL_CLASS_NAME;L$OBJECT_INTERNAL_CLASS_NAME;)L$OBJECT_INTERNAL_CLASS_NAME;"
    }
    val sortedLineNumbers = lineNumbers.sorted()
    result.instructions.apply {
        add(getStoreLineNumberInstructions())

        val invokeFunctionLabel = LabelNode()
        add(getGotoIfLastSpecInstructions(invokeFunctionLabel))

        val invalidLineNumberLabel = LabelNode()
        add(getInvokeNextSpecMethodInstructions(invalidLineNumberLabel, sortedLineNumbers))

        add(getReturnSuspendedMarkerIfResultIsSuspendedMarkerInstructions())

        add(invokeFunctionLabel)
        add(FrameNode(Opcodes.F_SAME, 0, null, 0, null))
        add(getResumeNextAndReturnInstructions(sortedLineNumbers))

        add(invalidLineNumberLabel)
        add(FrameNode(Opcodes.F_SAME, 0, null, 0, null))
        add(getThrowInvalidLineNumberInstructions())
    }
    return result
}

private const val SPEC_VAR_INDEX = 0
private const val RESULT_VAR_INDEX = 1
private const val LINE_NUMBER_VAR_INDEX = 2

private val METHOD_HANDLE_INTERNAL_CLASS_NAME = Type.getInternalName(MethodHandle::class.java)
private val SPEC_INTERNAL_CLASS_NAME = Type.getInternalName(DecoroutinatorSpec::class.java)
private val OBJECT_INTERNAL_CLASS_NAME = Type.getInternalName(Object::class.java)
private val STRING_BUILDER_INTERNAL_CLASS_NAME = Type.getInternalName(java.lang.StringBuilder::class.java)
private val STRING_INTERNAL_CLASS_NAME = Type.getInternalName(java.lang.String::class.java)

private fun getStoreLineNumberInstructions() = InsnList().apply {
    add(VarInsnNode(Opcodes.ALOAD, SPEC_VAR_INDEX))
    add(MethodInsnNode(
        Opcodes.INVOKEINTERFACE,
        SPEC_INTERNAL_CLASS_NAME,
        "getLineNumber",
        "()I"
    ))
    add(VarInsnNode(Opcodes.ISTORE, LINE_NUMBER_VAR_INDEX))
}

private fun getGotoIfLastSpecInstructions(label: LabelNode) = InsnList().apply {
    add(VarInsnNode(Opcodes.ALOAD, SPEC_VAR_INDEX))
    add(MethodInsnNode(
        Opcodes.INVOKEINTERFACE,
        SPEC_INTERNAL_CLASS_NAME,
        "isLastSpec",
        "()Z"
    ))
    add(JumpInsnNode(Opcodes.IFNE, label))
}

private fun getInvokeNextSpecMethodInstructions(
    invalidLineNumberLabel: LabelNode,
    lineNumbers: List<Int>
) = InsnList().apply {
    add(VarInsnNode(Opcodes.ALOAD, SPEC_VAR_INDEX))
    add(MethodInsnNode(
        Opcodes.INVOKEINTERFACE,
        SPEC_INTERNAL_CLASS_NAME,
        "getNextHandle",
        "()L$METHOD_HANDLE_INTERNAL_CLASS_NAME;"
    ))
    add(VarInsnNode(Opcodes.ALOAD, SPEC_VAR_INDEX))
    add(MethodInsnNode(
        Opcodes.INVOKEINTERFACE,
        SPEC_INTERNAL_CLASS_NAME,
        "getNextSpec",
        "()L$OBJECT_INTERNAL_CLASS_NAME;"
    ))
    add(VarInsnNode(Opcodes.ALOAD, RESULT_VAR_INDEX))
    val invalidLabel = LabelNode()
    val endLabel = LabelNode()
    addByLineNumbers(invalidLabel, lineNumbers, false) {
        add(FrameNode(
            Opcodes.F_FULL,
            3,
            arrayOf(
                SPEC_INTERNAL_CLASS_NAME,
                OBJECT_INTERNAL_CLASS_NAME,
                Opcodes.INTEGER,
            ),
            3,
            arrayOf(
                METHOD_HANDLE_INTERNAL_CLASS_NAME,
                OBJECT_INTERNAL_CLASS_NAME,
                OBJECT_INTERNAL_CLASS_NAME
            )
        ))
        add(MethodInsnNode(
            Opcodes.INVOKEVIRTUAL,
            METHOD_HANDLE_INTERNAL_CLASS_NAME,
            "invoke",
            "(L$OBJECT_INTERNAL_CLASS_NAME;L$OBJECT_INTERNAL_CLASS_NAME;)L$OBJECT_INTERNAL_CLASS_NAME;"
        ))
        add(JumpInsnNode(Opcodes.GOTO, endLabel))
    }
    add(invalidLabel)
    add(FrameNode(
        Opcodes.F_FULL,
        3,
        arrayOf(
            SPEC_INTERNAL_CLASS_NAME,
            OBJECT_INTERNAL_CLASS_NAME,
            Opcodes.INTEGER,
        ),
        3,
        arrayOf(
            METHOD_HANDLE_INTERNAL_CLASS_NAME,
            OBJECT_INTERNAL_CLASS_NAME,
            OBJECT_INTERNAL_CLASS_NAME
        )
    ))
    add(InsnNode(Opcodes.POP2))
    add(InsnNode(Opcodes.POP))
    add(JumpInsnNode(Opcodes.GOTO, invalidLineNumberLabel))
    add(endLabel)
    add(FrameNode(Opcodes.F_SAME1, 0, null, 1, arrayOf(OBJECT_INTERNAL_CLASS_NAME)))
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
            add(JumpInsnNode(Opcodes.GOTO, endLabel))
        }
    }
    if (endLabel != null) {
        add(endLabel)
    }
}

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

private fun getReturnSuspendedMarkerIfResultIsSuspendedMarkerInstructions() = InsnList().apply {
    add(VarInsnNode(Opcodes.ALOAD, RESULT_VAR_INDEX))
    add(VarInsnNode(Opcodes.ALOAD, SPEC_VAR_INDEX))
    add(MethodInsnNode(
        Opcodes.INVOKEINTERFACE,
        SPEC_INTERNAL_CLASS_NAME,
        "getCoroutineSuspendedMarker",
        "()L$OBJECT_INTERNAL_CLASS_NAME;"
    ))
    val endLabel = LabelNode()
    add(JumpInsnNode(Opcodes.IF_ACMPNE, endLabel))
    add(VarInsnNode(Opcodes.ALOAD, RESULT_VAR_INDEX))
    add(InsnNode(Opcodes.ARETURN))
    add(endLabel)
}

private fun getResumeNextAndReturnInstructions(lineNumbers: List<Int>) = InsnList().apply {
    add(VarInsnNode(Opcodes.ALOAD, SPEC_VAR_INDEX))
    add(VarInsnNode(Opcodes.ALOAD, RESULT_VAR_INDEX))
    val invalidLabel = LabelNode()
    addByLineNumbers(invalidLabel, lineNumbers) {
        add(FrameNode(
            Opcodes.F_FULL,
            3,
            arrayOf(
                SPEC_INTERNAL_CLASS_NAME,
                OBJECT_INTERNAL_CLASS_NAME,
                Opcodes.INTEGER,
            ),
            2,
            arrayOf(
                SPEC_INTERNAL_CLASS_NAME,
                OBJECT_INTERNAL_CLASS_NAME,
            )
        ))
        add(MethodInsnNode(
            Opcodes.INVOKEINTERFACE,
            SPEC_INTERNAL_CLASS_NAME,
            "resumeNext",
            "(L$OBJECT_INTERNAL_CLASS_NAME;)L$OBJECT_INTERNAL_CLASS_NAME;"
        ))
    }
    add(FrameNode(Opcodes.F_SAME1, 0, null, 1, arrayOf(OBJECT_INTERNAL_CLASS_NAME)))
    add(InsnNode(Opcodes.ARETURN))
    add(invalidLabel)
    add(FrameNode(
        Opcodes.F_FULL,
        3,
        arrayOf(
            SPEC_INTERNAL_CLASS_NAME,
            OBJECT_INTERNAL_CLASS_NAME,
            Opcodes.INTEGER,
        ),
        2,
        arrayOf(
            SPEC_INTERNAL_CLASS_NAME,
            OBJECT_INTERNAL_CLASS_NAME,
        )
    ))
    add(InsnNode(Opcodes.POP2))
}
