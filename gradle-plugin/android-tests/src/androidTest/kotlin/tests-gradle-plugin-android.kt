import dev.reformator.bytecodeprocessor.intrinsics.currentFileName
import dev.reformator.bytecodeprocessor.intrinsics.currentLineNumber
import dev.reformator.stacktracedecoroutinator.test.checkStacktrace
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.yield
import org.junit.Test
import org.junit.jupiter.api.Assertions.*
import kotlin.coroutines.resume

class TestLocalFile {
    @Test
    fun localTest(): Unit = runBlocking {
        fun1()
    }

    suspend fun fun1() {
        fun2(currentLineNumber)
    }

    suspend fun fun2(fun1LineNumber: Int) {
        val fun1Frame = StackTraceElement(
            TestLocalFile::class.qualifiedName,
            ::fun1.name,
            currentFileName,
            fun1LineNumber
        )
        yield()
        fun3(fun1Frame, currentLineNumber)
    }

    suspend fun fun3(fun1Frame: StackTraceElement, fun2LineNumber: Int) {
        val fun2Frame = StackTraceElement(
            TestLocalFile::class.qualifiedName,
            ::fun2.name,
            currentFileName,
            fun2LineNumber
        )
        yield()
        checkStacktrace(fun2Frame, fun1Frame)
    }
}

class TailCallDeoptimizeTest: dev.reformator.stacktracedecoroutinator.test.TailCallDeoptimizeTest() {
    @Test
    fun performBasic() {
        basic()
    }

    @Test
    fun performInterfaceWithDefaultMethodImpl() {
        interfaceWithDefaultMethodImpl()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DebugProbesTest {
    @Test
    fun performDebugProbes() {
        runBlocking {
            suspendCancellableCoroutine { continuation ->
                assertTrue(DebugProbes.dumpCoroutinesInfo().isNotEmpty())
                continuation.resume(Unit)
            }
        }
    }
}
