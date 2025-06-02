import android.os.Build
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.yield
import org.junit.Test
import org.junit.jupiter.api.Assertions.*
import kotlin.coroutines.resume

class LegacyAndroidTest {
    @Test
    fun basic() {
        val exception = runBlocking {
            fun0()
        }
        if (Build.VERSION.SDK_INT >= 26) {
            assertTrue(exception.stackTrace.find { it.methodName == "fun0" } != null)
            assertTrue(exception.stackTrace.find { it.methodName == "fun1" } != null)
        }
    }
}

private suspend fun fun0(): Exception =
    fun1()

private suspend fun fun1(): Exception =
    fun2()

private suspend fun fun2(): Exception {
    yield()
    return Exception("check")
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
