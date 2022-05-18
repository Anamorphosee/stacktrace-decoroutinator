package dev.reformator.stacktracedecoroutinator.android

import com.android.dex.DexFormat
import com.android.dx.dex.DexOptions
import com.android.dx.dex.code.*
import com.android.dx.dex.code.CstInsn
import com.android.dx.dex.file.ClassDefItem
import com.android.dx.dex.file.DexFile
import com.android.dx.dex.file.EncodedMethod
import com.android.dx.rop.code.*
import com.android.dx.rop.cst.*
import com.android.dx.rop.type.StdTypeList
import com.android.dx.rop.type.Type
import com.android.dx.util.IntList
import dalvik.system.InMemoryDexClassLoader
import dev.reformator.stacktracedecoroutinator.common.BaseDecoroutinatorStacktraceMethodHandleRegistry
import java.lang.reflect.Modifier
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.BiFunction

object DecoroutinatorAndroidStacktraceMethodHandleRegistryImpl: BaseDecoroutinatorStacktraceMethodHandleRegistry() {
    private val biFunctionType = Type.internClassName(
        BiFunction::class.java.typeName.replace('.', '/')
    )
    private val illegalArgumentExceptionType = Type.internClassName(
        java.lang.IllegalArgumentException::class.java.typeName.replace('.', '/')
    )
    private val stringBuilderType = Type.internClassName(
        java.lang.StringBuilder::class.java.typeName.replace('.', '/')
    )

    private val methodDesc = CstString(
            "(" +
            Type.METHOD_HANDLE.arrayType.descriptor +
            Type.INT_ARRAY.descriptor +
            Type.INT.descriptor +
            biFunctionType.descriptor +
            Type.OBJECT.descriptor +
            Type.OBJECT.descriptor +
            ")" +
            Type.OBJECT.descriptor
    )

    private val stacktraceHandlers = RegisterSpec.make(2, Type.METHOD_HANDLE.arrayType)
    private val stacktraceLineNumbers = RegisterSpec.make(3, Type.INT_ARRAY)
    private val stacktraceNextStepIndex = RegisterSpec.make(4, Type.INT)
    private val invokeCoroutineFunc = RegisterSpec.make(5, biFunctionType)
    private val coroutineResult = RegisterSpec.make(6, Type.OBJECT)
    private val suspendCoroutineConst = RegisterSpec.make(7, Type.OBJECT)
    private val lineNumber = RegisterSpec.make(0, Type.INT)
    private val auxInt = RegisterSpec.make(1, Type.INT)
    private val auxHandler = RegisterSpec.make(1, Type.METHOD_HANDLE)
    private val auxInteger = RegisterSpec.make(1, Type.INTEGER_CLASS)
    private val auxString = RegisterSpec.make(1, Type.STRING)
    private val auxStringBuilder = RegisterSpec.make(2, stringBuilderType)
    private val auxIllegalArgumentException = RegisterSpec.make(2, illegalArgumentExceptionType)

    private val loadersCache = CopyOnWriteArrayList<InMemoryDexClassLoader>()

    override fun generateStacktraceClass(
        className: String,
        fileName: String?,
        classRevision: Int,
        methodName2LineNumbers: Map<String, Set<Int>>
    ): Class<*> {
        val fileNameCstString = fileName?.let { CstString(it) }

        val dexOptions = DexOptions()
        dexOptions.minSdkVersion = DexFormat.API_METHOD_HANDLES

        val dexFile = DexFile(dexOptions)

        val type = CstType(Type.internClassName(className.replace('.', '/')))

        val classDef = ClassDefItem(
            type,
            Modifier.PUBLIC or Modifier.FINAL,
            CstType.OBJECT,
            StdTypeList.EMPTY,
            fileNameCstString
        )

        methodName2LineNumbers.forEach { (methodName, lineNumbers) ->
            val context = MethodContext(fileNameCstString, lineNumbers)
            val outputFinisher = OutputFinisher(dexOptions, 0, 8, 6).apply {
                addStoreLineNumberInstructions()
                val invokeSuspendCoroutineLabel = CodeAddress(SourcePosition.NO_INFO)
                addGotoIfLastFrameInstructions(invokeSuspendCoroutineLabel)
                addInvokeNextFrameInstructions(context)
                addReturnIfCoroutineResultIsSuspendInstructions()
                add(invokeSuspendCoroutineLabel)
                addInvokeCoroutineAndReturnInstructions(context)
                add(context.invalidLineLabel)
                addThrowInvalidLineNumberInstructions()
            }


            val code = DalvCode(
                PositionList.LINES,
                outputFinisher,
                object: CatchBuilder {
                    override fun build() = CatchTable.EMPTY
                    override fun hasAnyCatches() = false
                    override fun getCatchTypes() = TODO()
                }
            )

            val method = EncodedMethod(
                CstMethodRef(type, CstNat(CstString(methodName), methodDesc)),
                Modifier.PUBLIC or Modifier.STATIC or Modifier.FINAL,
                code,
                StdTypeList.EMPTY
            )

            classDef.addDirectMethod(method)
        }

        dexFile.add(classDef)

        val body = dexFile.toDex(null, false)

        return InMemoryDexClassLoader(ByteBuffer.wrap(body), ClassLoader.getSystemClassLoader()).also {
            loadersCache.add(it)
        }.loadClass(className)
    }

    private fun OutputFinisher.addStoreLineNumberInstructions() {
        add(CstInsn(
            Dops.ADD_INT_LIT8,
            SourcePosition.NO_INFO,
            RegisterSpecList.make(auxInt, stacktraceNextStepIndex),
            CstInteger.VALUE_M1
        ))

        add(SimpleInsn(
            Dops.AGET,
            SourcePosition.NO_INFO,
            RegisterSpecList.make(lineNumber, stacktraceLineNumbers, auxInt)
        ))
    }

    private fun OutputFinisher.addGotoIfLastFrameInstructions(label: CodeAddress) {
        add(SimpleInsn(
            Dops.ARRAY_LENGTH,
            SourcePosition.NO_INFO,
            RegisterSpecList.make(auxInt, stacktraceHandlers)
        ))

        add(TargetInsn(
            Dops.IF_EQ,
            SourcePosition.NO_INFO,
            RegisterSpecList.make(auxInt, stacktraceNextStepIndex),
            label
        ))
    }

    private fun OutputFinisher.addInvokeNextFrameInstructions(context: MethodContext) {
        add(SimpleInsn(
            Dops.AGET_OBJECT,
            SourcePosition.NO_INFO,
            RegisterSpecList.make(auxHandler, stacktraceHandlers, stacktraceNextStepIndex)
        ))

        add(CstInsn(
            Dops.ADD_INT_LIT8,
            SourcePosition.NO_INFO,
            RegisterSpecList.make(stacktraceNextStepIndex, stacktraceNextStepIndex),
            CstInteger.VALUE_1
        ))

        instructionsByLineNumbers(context) { sourcePosition ->
            add(MultiCstInsn(
                Dops.INVOKE_POLYMORPHIC,
                sourcePosition,
                RegisterSpecList(7).apply {
                    this[0] = auxHandler
                    this[1] = stacktraceHandlers
                    this[2] = stacktraceLineNumbers
                    this[3] = stacktraceNextStepIndex
                    this[4] = invokeCoroutineFunc
                    this[5] = coroutineResult
                    this[6] = suspendCoroutineConst
                    setImmutable()
                },
                arrayOf(
                    CstMethodRef(
                        CstType(Type.METHOD_HANDLE),
                        CstNat(
                            CstString("invokeExact"),
                            CstString("(${Type.OBJECT_ARRAY.descriptor})${Type.OBJECT.descriptor}")
                        )
                    ),
                    CstProtoRef.make(methodDesc)
                )
            ))

            add(SimpleInsn(
                Dops.MOVE_RESULT_OBJECT,
                sourcePosition,
                RegisterSpecList.make(coroutineResult)
            ))

            add(CstInsn(
                Dops.ADD_INT_LIT8,
                SourcePosition.NO_INFO,
                RegisterSpecList.make(stacktraceNextStepIndex, stacktraceNextStepIndex),
                CstInteger.VALUE_M1
            ))
        }
    }

    private fun OutputFinisher.addReturnIfCoroutineResultIsSuspendInstructions() {
        val endLabel = CodeAddress(SourcePosition.NO_INFO)
        add(TargetInsn(
            Dops.IF_NE,
            SourcePosition.NO_INFO,
            RegisterSpecList.make(coroutineResult, suspendCoroutineConst),
            endLabel
        ))
        add(SimpleInsn(
            Dops.RETURN_OBJECT,
            SourcePosition.NO_INFO,
            RegisterSpecList.make(suspendCoroutineConst)
        ))
        add(endLabel)
    }

    private fun OutputFinisher.addInvokeCoroutineAndReturnInstructions(context: MethodContext) {
        add(CstInsn(
            Dops.INVOKE_STATIC,
            SourcePosition.NO_INFO,
            RegisterSpecList.make(stacktraceNextStepIndex),
            CstMethodRef(
                CstType(Type.INTEGER_CLASS),
                CstNat(
                    CstString("valueOf"),
                    CstString("(${Type.INT.descriptor})${Type.INTEGER_CLASS.descriptor}")
                )
            )
        ))
        add(SimpleInsn(
            Dops.MOVE_RESULT_OBJECT,
            SourcePosition.NO_INFO,
            RegisterSpecList.make(auxInteger)
        ))
        instructionsByLineNumbers(context, false) { sourcePosition ->
            add(CstInsn(
                Dops.INVOKE_INTERFACE,
                sourcePosition,
                RegisterSpecList.make(invokeCoroutineFunc, auxInteger, coroutineResult),
                CstMethodRef(
                    CstType(biFunctionType),
                    CstNat(
                        CstString("apply"),
                        CstString("(${Type.OBJECT.descriptor}${Type.OBJECT.descriptor})${Type.OBJECT.descriptor}")
                    )
                )
            ))
            add(SimpleInsn(
                Dops.MOVE_RESULT_OBJECT,
                sourcePosition,
                RegisterSpecList.make(coroutineResult)
            ))
            add(SimpleInsn(
                Dops.RETURN_OBJECT,
                SourcePosition.NO_INFO,
                RegisterSpecList.make(coroutineResult)
            ))
        }
    }

    private fun OutputFinisher.addThrowInvalidLineNumberInstructions() {
        add(CstInsn(
            Dops.CONST_STRING,
            SourcePosition.NO_INFO,
            RegisterSpecList.make(auxString),
            CstString("invalid line number: ")
        ))

        add(CstInsn(
            Dops.NEW_INSTANCE,
            SourcePosition.NO_INFO,
            RegisterSpecList.make(auxStringBuilder),
            CstType(stringBuilderType)
        ))
        add(CstInsn(
            Dops.INVOKE_DIRECT,
            SourcePosition.NO_INFO,
            RegisterSpecList.make(auxStringBuilder, auxString),
            CstMethodRef(
                CstType(stringBuilderType),
                CstNat(
                    CstString("<init>"),
                    CstString("(${Type.STRING.descriptor})${Type.VOID.descriptor}")
                )
            )
        ))

        add(CstInsn(
            Dops.INVOKE_VIRTUAL,
            SourcePosition.NO_INFO,
            RegisterSpecList.make(auxStringBuilder, lineNumber),
            CstMethodRef(
                CstType(stringBuilderType),
                CstNat(
                    CstString("append"),
                    CstString("(${Type.INT.descriptor})${stringBuilderType.descriptor}")
                )
            )
        ))
        add(SimpleInsn(
            Dops.MOVE_RESULT_OBJECT,
            SourcePosition.NO_INFO,
            RegisterSpecList.make(auxStringBuilder)
        ))

        add(CstInsn(
            Dops.INVOKE_VIRTUAL,
            SourcePosition.NO_INFO,
            RegisterSpecList.make(auxStringBuilder),
            CstMethodRef(
                CstType(stringBuilderType),
                CstNat(
                    CstString("toString"),
                    CstString("()${Type.STRING.descriptor}")
                )
            )
        ))
        add(SimpleInsn(
            Dops.MOVE_RESULT_OBJECT,
            SourcePosition.NO_INFO,
            RegisterSpecList.make(auxString)
        ))

        add(CstInsn(
            Dops.NEW_INSTANCE,
            SourcePosition.NO_INFO,
            RegisterSpecList.make(auxIllegalArgumentException),
            CstType(illegalArgumentExceptionType)
        ))
        add(CstInsn(
            Dops.INVOKE_DIRECT,
            SourcePosition.NO_INFO,
            RegisterSpecList.make(auxIllegalArgumentException, auxString),
            CstMethodRef(
                CstType(illegalArgumentExceptionType),
                CstNat(
                    CstString("<init>"),
                    CstString("(${Type.STRING.descriptor})${Type.VOID.descriptor}")
                )
            )
        ))

        add(SimpleInsn(
            Dops.THROW,
            SourcePosition.NO_INFO,
            RegisterSpecList.make(auxIllegalArgumentException)
        ))
    }

    private fun OutputFinisher.instructionsByLineNumbers(
        context: MethodContext,
        gotoEnd: Boolean = true,
        addInstructions: OutputFinisher.(SourcePosition) -> Unit
    ) {
        val switchLabel = CodeAddress(SourcePosition.NO_INFO)
        val dataLabel = CodeAddress(SourcePosition.NO_INFO)
        val labels = Array(context.lineNumbers.size()) {
            CodeAddress(SourcePosition.NO_INFO)
        }
        val switchData = SwitchData(
            SourcePosition.NO_INFO,
            switchLabel,
            context.lineNumbers,
            labels
        )

        add(switchLabel)
        add(TargetInsn(
            if (switchData.isPacked) Dops.PACKED_SWITCH else Dops.SPARSE_SWITCH,
            SourcePosition.NO_INFO,
            RegisterSpecList.make(lineNumber),
            dataLabel
        ))

        add(TargetInsn(
            Dops.GOTO,
            SourcePosition.NO_INFO,
            RegisterSpecList.EMPTY,
            context.invalidLineLabel
        ))

        add(OddSpacer(SourcePosition.NO_INFO))
        add(dataLabel)
        add(switchData)

        val endLabel = if (gotoEnd) CodeAddress(SourcePosition.NO_INFO) else null
        (0 until context.lineNumbers.size()).forEach { index ->
            add(labels[index])
            addInstructions(SourcePosition(context.fileName, -1, context.lineNumbers[index]))
            if (endLabel != null && index < context.lineNumbers.size() - 1) {
                add(TargetInsn(
                    Dops.GOTO,
                    SourcePosition.NO_INFO,
                    RegisterSpecList.EMPTY,
                    endLabel
                ))
            }
        }

        if (endLabel != null) {
            add(endLabel)
        }
    }
}

private data class MethodContext private constructor(
    val fileName: CstString?,
    val lineNumbers: IntList
) {
    val invalidLineLabel = CodeAddress(SourcePosition.NO_INFO)

    constructor(
        fileName: CstString?,
        lineNumbers: Set<Int>
    ): this(
        fileName = fileName,
        lineNumbers = IntList(lineNumbers.size).apply {
            lineNumbers.asSequence().sorted().forEach { lineNumber ->
                add(lineNumber)
            }
            setImmutable()
        }
    )
}
