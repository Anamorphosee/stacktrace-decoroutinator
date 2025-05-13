import org.junit.jupiter.api.Test

class JvmTest: dev.reformator.stacktracedecoroutinator.testjvm.JvmTest() {
    @Test
    fun performClassesWithSpaces() {
        `class with spaces`(false)
    }
}

class CustomClassLoaderTest: dev.reformator.stacktracedecoroutinator.test.CustomClassLoaderTest() {
    @Test
    fun performBasic() {
        basic(true)
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
