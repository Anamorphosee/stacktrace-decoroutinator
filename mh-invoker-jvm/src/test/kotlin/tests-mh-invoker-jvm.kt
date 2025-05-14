import org.junit.jupiter.api.Test

class PerformanceTest: dev.reformator.stacktracedecoroutinator.test.PerformanceTest()
class RuntimeTest: dev.reformator.stacktracedecoroutinator.test.RuntimeTest()

class CustomClassLoaderTest: dev.reformator.stacktracedecoroutinator.test.CustomClassLoaderTest() {
    @Test
    fun performBasic() {
        basic(true)
    }
}

class JvmTest: dev.reformator.stacktracedecoroutinator.testjvm.JvmTest() {
    @Test
    fun performClassWithSpaces() {
        `class with spaces`(true)
    }
}
