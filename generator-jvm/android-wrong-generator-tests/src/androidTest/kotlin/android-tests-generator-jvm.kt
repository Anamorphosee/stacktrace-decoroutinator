import dev.reformator.stacktracedecoroutinator.common.internal.methodHandleInvoker
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidTestsGeneratorJvm {
    @Test
    fun runAndroidWithGeneratorJvm() {
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