import org.junit.jupiter.api.Test

class RuntimeTest: dev.reformator.stacktracedecoroutinator.test.RuntimeTest()

class CustomClassLoaderTest: dev.reformator.stacktracedecoroutinator.test.CustomClassLoaderTest() {
    @Test
    fun check() {
        performCheck(false)
    }
}
