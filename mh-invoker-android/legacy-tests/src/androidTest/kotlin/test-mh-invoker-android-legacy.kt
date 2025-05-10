import android.os.Build
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Test
import org.junit.jupiter.api.Assertions.*

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

private suspend fun fun0(): Exception {
    val result = fun1()
    tailCallDeoptimize()
    return result
}

private suspend fun fun1(): Exception {
    val result = fun2()
    tailCallDeoptimize()
    return result
}

private suspend fun fun2(): Exception {
    yield()
    return Exception("check")
}

private fun tailCallDeoptimize() { }