@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("SpecMethodBuilderGeneratorAndroidKt")

package dev.reformator.stacktracedecoroutinator.generatorandroid

import com.android.dx.dex.DexOptions
import com.android.dx.dex.code.*
import com.android.dx.dex.file.EncodedMethod
import com.android.dx.rop.code.RegisterSpec
import com.android.dx.rop.code.RegisterSpecList
import com.android.dx.rop.code.SourcePosition
import com.android.dx.rop.cst.*
import com.android.dx.rop.type.StdTypeList
import com.android.dx.rop.type.Type
import com.android.dx.util.IntList
import dev.reformator.bytecodeprocessor.intrinsics.LoadConstant
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import java.lang.invoke.MethodHandle
import java.lang.reflect.Modifier

internal fun buildSpecMethod(
    dexOptions: DexOptions,
    clazz: CstType,
    fileName: CstString?,
    methodName: String,
    lineNumbers: Set<Int>
): EncodedMethod {
    val finisher = OutputFinisher(dexOptions, 0, 5, 2)

    val lineNumbersIntList = IntList(lineNumbers.size).apply {
        lineNumbers.asSequence().sorted().forEach { lineNumber ->
            add(lineNumber)
        }
        setImmutable()
    }

    finisher.saveLineNumber()
    val resumeNextLabel = CodeAddress(SourcePosition.NO_INFO)
    finisher.gotoIfLastSpec(resumeNextLabel)
    val invalidLineLabel = CodeAddress(SourcePosition.NO_INFO)
    finisher.callNextSpec(
        fileName = fileName,
        lineNumbers = lineNumbersIntList,
        invalidLineLabel = invalidLineLabel
    )
    finisher.returnSuspendedCoroutineMarkerIfResultIsIt()
    finisher.add(resumeNextLabel)
    finisher.resumeNext(
        fileName = fileName,
        lineNumbers = lineNumbersIntList,
        invalidLineLabel = invalidLineLabel
    )
    finisher.add(SimpleInsn(
        Dops.RETURN_OBJECT,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(result)
    ))
    finisher.add(invalidLineLabel)
    finisher.throwInvalidLine()

    val code = DalvCode(
        PositionList.LINES,
        finisher,
        object: CatchBuilder {
            override fun build() = CatchTable.EMPTY
            override fun hasAnyCatches() = false
            override fun getCatchTypes() = error("something wrong")
        }
    )

    return EncodedMethod(
        CstMethodRef(clazz, CstNat(CstString(methodName), specMethodDesc)),
        Modifier.PUBLIC or Modifier.STATIC or Modifier.FINAL,
        code,
        StdTypeList.EMPTY
    )
}

internal val isolatedSpecClassName: String
    @LoadConstant get() { fail() }

internal val String.internalName: String
    get() = replace('.', '/')

private val specClass = Type.internClassName(isolatedSpecClassName.internalName)
private val methodHandleClass = Type.internClassName(MethodHandle::class.java.name.internalName)
private val illegalArgumentExceptionClass = Type.internClassName(
    java.lang.IllegalArgumentException::class.java.name.internalName
)
private val stringBuilderClass = Type.internClassName(
    java.lang.StringBuilder::class.java.name.internalName
)

private val specMethodDesc = CstString("(${specClass.descriptor}${Type.OBJECT.descriptor})${Type.OBJECT.descriptor}")

private val spec = RegisterSpec.make(3, specClass)
private val result = RegisterSpec.make(4, Type.OBJECT)
private val lineNumber = RegisterSpec.make(0, Type.INT)
private val aux1Boolean = RegisterSpec.make(1, Type.BOOLEAN)
private val aux1MethodHandle = RegisterSpec.make(1, methodHandleClass)
private val aux1Object = RegisterSpec.make(1, Type.OBJECT)
private val aux2Object = RegisterSpec.make(2, Type.OBJECT)
private val aux1String = RegisterSpec.make(1, Type.STRING)
private val aux2StringBuilder = RegisterSpec.make(2, stringBuilderClass)
private val aux2IllegalArgumentException = RegisterSpec.make(2, illegalArgumentExceptionClass)


private fun OutputFinisher.saveLineNumber() {
    add(CstInsn(
        Dops.INVOKE_VIRTUAL,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(spec),
        CstMethodRef(
            CstType(specClass),
            CstNat(
                CstString(getGetterMethodName(DecoroutinatorSpec::lineNumber.name)),
                CstString("()${Type.INT.descriptor}")
            )
        )
    ))
    add(SimpleInsn(
        Dops.MOVE_RESULT,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(lineNumber)
    ))
}

private fun OutputFinisher.gotoIfLastSpec(label: CodeAddress) {
    add(CstInsn(
        Dops.INVOKE_VIRTUAL,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(spec),
        CstMethodRef(
            CstType(specClass),
            CstNat(
                CstString(getGetterMethodName(DecoroutinatorSpec::isLastSpec.name)),
                CstString("()${Type.BOOLEAN.descriptor}")
            )
        )
    ))
    add(SimpleInsn(
        Dops.MOVE_RESULT,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(aux1Boolean)
    ))
    add(TargetInsn(
        Dops.IF_NEZ,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(aux1Boolean),
        label
    ))
}

private fun OutputFinisher.callNextSpec(
    fileName: CstString?,
    lineNumbers: IntList,
    invalidLineLabel: CodeAddress
) {
    add(CstInsn(
        Dops.INVOKE_VIRTUAL,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(spec),
        CstMethodRef(
            CstType(specClass),
            CstNat(
                CstString(getGetterMethodName(DecoroutinatorSpec::nextSpecHandle.name)),
                CstString("()${methodHandleClass.descriptor}")
            )
        )
    ))
    add(SimpleInsn(
        Dops.MOVE_RESULT_OBJECT,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(aux1MethodHandle)
    ))
    add(CstInsn(
        Dops.INVOKE_VIRTUAL,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(spec),
        CstMethodRef(
            CstType(specClass),
            CstNat(
                CstString(getGetterMethodName(DecoroutinatorSpec::nextSpec.name)),
                CstString("()${Type.OBJECT.descriptor}")
            )
        )
    ))
    add(SimpleInsn(
        Dops.MOVE_RESULT_OBJECT,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(aux2Object)
    ))
    instructionsByLineNumbers(
        fileName = fileName,
        lineNumbers = lineNumbers,
        invalidLineLabel = invalidLineLabel
    ) {
        add(MultiCstInsn(
            Dops.INVOKE_POLYMORPHIC,
            it,
            RegisterSpecList.make(aux1MethodHandle, aux2Object, result),
            arrayOf(
                CstMethodRef(
                    CstType(Type.METHOD_HANDLE),
                    CstNat(
                        CstString("invoke"),
                        CstString("(${Type.OBJECT_ARRAY.descriptor})${Type.OBJECT.descriptor}")
                    )
                ),
                CstProtoRef.make(CstString(
                    "(${Type.OBJECT.descriptor}${Type.OBJECT.descriptor})${Type.OBJECT.descriptor}"
                ))
            )
        ))
        add(SimpleInsn(
            Dops.MOVE_RESULT_OBJECT,
            it,
            RegisterSpecList.make(result)
        ))
    }
}

private fun OutputFinisher.returnSuspendedCoroutineMarkerIfResultIsIt() {
    add(CstInsn(
        Dops.INVOKE_VIRTUAL,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(spec),
        CstMethodRef(
            CstType(specClass),
            CstNat(
                CstString(getGetterMethodName(DecoroutinatorSpec::coroutineSuspendedMarker.name)),
                CstString("()${Type.OBJECT.descriptor}")
            )
        )
    ))
    add(SimpleInsn(
        Dops.MOVE_RESULT_OBJECT,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(aux1Object)
    ))
    val endLabel = CodeAddress(SourcePosition.NO_INFO)
    add(TargetInsn(
        Dops.IF_NE,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(result, aux1Object),
        endLabel
    ))
    add(SimpleInsn(
        Dops.RETURN_OBJECT,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(aux1Object)
    ))
    add(endLabel)
}

private fun OutputFinisher.resumeNext(
    fileName: CstString?,
    lineNumbers: IntList,
    invalidLineLabel: CodeAddress
) {
    instructionsByLineNumbers(
        fileName = fileName,
        lineNumbers = lineNumbers,
        invalidLineLabel = invalidLineLabel
    ) {
        add(CstInsn(
            Dops.INVOKE_VIRTUAL,
            it,
            RegisterSpecList.make(spec, result),
            CstMethodRef(
                CstType(specClass),
                CstNat(
                    CstString(DecoroutinatorSpec::resumeNext.name),
                    CstString("(${Type.OBJECT.descriptor})${Type.OBJECT.descriptor}")
                )
            )
        ))
        add(SimpleInsn(
            Dops.MOVE_RESULT_OBJECT,
            it,
            RegisterSpecList.make(result)
        ))
    }
}

private fun OutputFinisher.throwInvalidLine() {
    add(CstInsn(
        Dops.CONST_STRING,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(aux1String),
        CstString("invalid line number: ")
    ))

    add(CstInsn(
        Dops.NEW_INSTANCE,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(aux2StringBuilder),
        CstType(stringBuilderClass)
    ))
    add(CstInsn(
        Dops.INVOKE_DIRECT,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(aux2StringBuilder, aux1String),
        CstMethodRef(
            CstType(stringBuilderClass),
            CstNat(
                CstString("<init>"),
                CstString("(${Type.STRING.descriptor})${Type.VOID.descriptor}")
            )
        )
    ))

    add(CstInsn(
        Dops.INVOKE_VIRTUAL,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(aux2StringBuilder, lineNumber),
        CstMethodRef(
            CstType(stringBuilderClass),
            CstNat(
                CstString("append"),
                CstString("(${Type.INT.descriptor})${stringBuilderClass.descriptor}")
            )
        )
    ))
    add(SimpleInsn(
        Dops.MOVE_RESULT_OBJECT,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(aux2StringBuilder)
    ))

    add(CstInsn(
        Dops.INVOKE_VIRTUAL,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(aux2StringBuilder),
        CstMethodRef(
            CstType(stringBuilderClass),
            CstNat(
                CstString("toString"),
                CstString("()${Type.STRING.descriptor}")
            )
        )
    ))
    add(SimpleInsn(
        Dops.MOVE_RESULT_OBJECT,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(aux1String)
    ))

    add(CstInsn(
        Dops.NEW_INSTANCE,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(aux2IllegalArgumentException),
        CstType(illegalArgumentExceptionClass)
    ))
    add(CstInsn(
        Dops.INVOKE_DIRECT,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(aux2IllegalArgumentException, aux1String),
        CstMethodRef(
            CstType(illegalArgumentExceptionClass),
            CstNat(
                CstString("<init>"),
                CstString("(${Type.STRING.descriptor})${Type.VOID.descriptor}")
            )
        )
    ))

    add(SimpleInsn(
        Dops.THROW,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(aux2IllegalArgumentException)
    ))
}

private fun OutputFinisher.instructionsByLineNumbers(
    fileName: CstString?,
    lineNumbers: IntList,
    invalidLineLabel: CodeAddress,
    addInstructions: OutputFinisher.(SourcePosition) -> Unit
) {
    val switchLabel = CodeAddress(SourcePosition.NO_INFO)
    val switchDataLabel = CodeAddress(SourcePosition.NO_INFO)
    val labels = Array(lineNumbers.size()) {
        CodeAddress(SourcePosition.NO_INFO)
    }
    val switchData = SwitchData(
        SourcePosition.NO_INFO,
        switchLabel,
        lineNumbers,
        labels
    )

    add(switchLabel)
    add(TargetInsn(
        if (switchData.isPacked) Dops.PACKED_SWITCH else Dops.SPARSE_SWITCH,
        SourcePosition.NO_INFO,
        RegisterSpecList.make(lineNumber),
        switchDataLabel
    ))
    add(TargetInsn(
        Dops.GOTO,
        SourcePosition.NO_INFO,
        RegisterSpecList.EMPTY,
        invalidLineLabel
    ))

    add(OddSpacer(SourcePosition.NO_INFO))
    add(switchDataLabel)
    add(switchData)

    val endLabel = CodeAddress(SourcePosition.NO_INFO)
    (0 until lineNumbers.size()).forEach { index ->
        add(labels[index])
        addInstructions(SourcePosition(fileName, -1, lineNumbers[index]))
        if (index < lineNumbers.size() - 1) {
            add(TargetInsn(
                Dops.GOTO,
                SourcePosition.NO_INFO,
                RegisterSpecList.EMPTY,
                endLabel
            ))
        }
    }
    add(endLabel)
}

private fun getGetterMethodName(propertyName: String): String =
    if (propertyName.startsWith("is")) propertyName else "get${propertyName[0].uppercase()}${propertyName.substring(1)}"