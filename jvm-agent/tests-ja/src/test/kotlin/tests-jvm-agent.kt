import dev.reformator.stacktracedecoroutinator.common.DecoroutinatorCommonApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertTrue

class PerformanceTest: dev.reformator.stacktracedecoroutinator.test.PerformanceTest()
class RuntimeTest: dev.reformator.stacktracedecoroutinator.test.RuntimeTest()

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

class CustomClassLoaderTest: dev.reformator.stacktracedecoroutinator.test.CustomClassLoaderTest() {
    @Test
    fun performBasic() {
        basic(false)
    }
}

class JvmTest: dev.reformator.stacktracedecoroutinator.testjvm.JvmTest() {
    @Test
    fun performClassWithSpaces() {
        `class with spaces`(false)
    }

    @Test
    fun checkStatus() {
        assertTrue(DecoroutinatorCommonApi.getStatus { it() }.successful)
    }
}
