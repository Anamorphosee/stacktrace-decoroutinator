import dev.reformator.stacktracedecoroutinator.test.CustomClassLoaderTest
import dev.reformator.stacktracedecoroutinator.test.PerformanceTest
import dev.reformator.stacktracedecoroutinator.test.RuntimeTest
import dev.reformator.stacktracedecoroutinator.testjvm.JvmTest
import org.junit.jupiter.api.Test

class PerformanceTest: PerformanceTest()
class RuntimeTest: RuntimeTest()

class CustomClassLoaderTest: CustomClassLoaderTest() {
    @Test
    fun performBasic() {
        basic(true)
    }
}

class JvmTest: JvmTest() {
    @Test
    fun performClassWithSpaces() {
        `class with spaces`(true)
    }
}
