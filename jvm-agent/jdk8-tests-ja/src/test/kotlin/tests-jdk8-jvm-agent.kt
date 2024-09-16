import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test

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
    fun localBasic() {
        basic()
    }

    @Test
    fun localInterfaceWithDefaultMethodImpl() {
        interfaceWithDefaultMethodImpl()
    }
}

class CustomClassLoaderTest: dev.reformator.stacktracedecoroutinator.test.CustomClassLoaderTest() {
    @Test
    fun check() {
        performCheck(true)
    }
}

class JvmTest: dev.reformator.stacktracedecoroutinator.testjvm.JvmTest() {
    @org.junit.jupiter.api.Test
    fun check() {
        `start class with spaces`(true)
    }
}
