import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.coroutines.resume

class RuntimeTest: dev.reformator.stacktracedecoroutinator.test.RuntimeTest()
class TailCallDeoptimizeTest: dev.reformator.stacktracedecoroutinator.test.TailCallDeoptimizeTest()
class CustomClassLoaderTest: dev.reformator.stacktracedecoroutinator.test.CustomClassLoaderTest()
class CustomClassLoaderTailCallDeoptimizedTest: dev.reformator.stacktracedecoroutinator.test.CustomClassLoaderTailCallDeoptimizedTest()
class JvmTest: dev.reformator.stacktracedecoroutinator.testjvm.JvmTest()
class JvmTailCallDeoptimizedTest: dev.reformator.stacktracedecoroutinator.testjvm.JvmTailCallDeoptimizedTest()

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
