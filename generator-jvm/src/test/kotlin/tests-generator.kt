import org.junit.jupiter.api.Test

class PerformanceTest: dev.reformator.stacktracedecoroutinator.test.PerformanceTest()
class RuntimeTest: dev.reformator.stacktracedecoroutinator.test.RuntimeTest()

class CustomClassLoaderTest: dev.reformator.stacktracedecoroutinator.test.CustomClassLoaderTest() {
    @Test
    fun check() {
        basic(true)
    }
}

class JvmTest: dev.reformator.stacktracedecoroutinator.testjvm.JvmTest() {
    @Test
    fun check() {
        `class with spaces`(true)
    }
}
