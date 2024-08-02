import dev.reformator.stacktracedecoroutinator.test.checkStacktrace
import dev.reformator.stacktracedecoroutinator.test.getFileName
import dev.reformator.stacktracedecoroutinator.test.getLineNumber
import dev.reformator.stacktracedecoroutinator.test.tailCallDeoptimize
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Test

class TestLocalFile {
    @Test
    fun localTest(): Unit = runBlocking {
        fun1()
    }

    suspend fun fun1() {
        fun2(getLineNumber())
        tailCallDeoptimize()
    }

    suspend fun fun2(fun1LineNumber: Int) {
        val fun1Frame = StackTraceElement(
            TestLocalFile::class.qualifiedName,
            "fun1",
            fileName,
            fun1LineNumber
        )
        yield()
        fun3(fun1Frame, getLineNumber())
        tailCallDeoptimize()
    }

    suspend fun fun3(fun1Frame: StackTraceElement, fun2LineNumber: Int) {
        val fun2Frame = StackTraceElement(
            TestLocalFile::class.qualifiedName,
            "fun2",
            fileName,
            fun2LineNumber
        )
        yield()
        checkStacktrace(fun2Frame, fun1Frame)
    }
}

private val fileName = getFileName()
