import dev.reformator.stacktracedecoroutinator.common.internal.methodHandleInvoker
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmTestsGeneratorAndroid {
    @Test
    fun runJvmWithGeneratorAndroid() {
        val message = "OK"

        suspend fun f() {
            yield()
            error(message)
        }

        try {
            runBlocking {
                f()
            }
        } catch (e: IllegalStateException) {
            assertEquals(message, e.message)
            assertTrue(e.stackTrace.any { it.className == methodHandleInvoker.unknownSpecMethodClass.name })
        }
    }
}