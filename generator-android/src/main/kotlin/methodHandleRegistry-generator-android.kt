@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.generatorandroid

import com.android.dx.rop.code.RegisterSpec
import com.android.dx.rop.cst.CstString
import com.android.dx.rop.type.Type
import dev.reformator.stacktracedecoroutinator.runtime.BaseMethodHandleRegistry
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.BiFunction

class AndroidMethodHandleRegistry: BaseMethodHandleRegistry() {
    override fun generateStacktraceClass(
        className: String,
        fileName: String?,
        classRevision: Int,
        methodName2LineNumbers: Map<String, Set<Int>>
    ): Class<*> {
        TODO("Not yet implemented")
    }

    private val loadersCache = CopyOnWriteArrayList<InMemoryDexClassLoader>()
}

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
