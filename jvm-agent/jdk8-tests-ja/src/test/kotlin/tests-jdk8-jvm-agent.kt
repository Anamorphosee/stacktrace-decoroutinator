import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test

class RuntimeTest: dev.reformator.stacktracedecoroutinator.test.RuntimeTest()
class TailCallDeoptimizeTest: dev.reformator.stacktracedecoroutinator.test.TailCallDeoptimizeTest()
class CustomClassLoaderTest: dev.reformator.stacktracedecoroutinator.test.CustomClassLoaderTest()
class CustomClassLoaderTailCallDeoptimizedTest: dev.reformator.stacktracedecoroutinator.test.CustomClassLoaderTailCallDeoptimizedTest()
class JvmTest: dev.reformator.stacktracedecoroutinator.testjvm.JvmTest()
class JvmTailCallDeoptimizedTest: dev.reformator.stacktracedecoroutinator.testjvm.JvmTailCallDeoptimizedTest()

// Jacoco only instruments this class
class JacocoInstrumentedMethodTest {
    @Test
    fun jacocoInstrumentedMethodTest(): Unit = runBlocking {
        suspend fun jacocoInstrumentedMethod() {
            yield()
            yield()
        }
        jacocoInstrumentedMethod()
    }
}
