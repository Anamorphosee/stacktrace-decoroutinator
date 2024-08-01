import dev.reformator.stacktracedecoroutinator.runtime.BASE_CONTINUATION_CLASS_NAME
import dev.reformator.stacktracedecoroutinator.runtime.DecoroutinatorRuntime
import dev.reformator.stacktracedecoroutinator.runtime.isDecoroutinatorBaseContinuation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.condition.DisabledIfSystemProperty
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisabledIfSystemProperty(named = "testReloadBaseConfiguration", matches = "true")
class PerformanceTest: dev.reformator.stacktracedecoroutinator.test.PerformanceTest() {
    @BeforeTest
    fun setup() {
        setupTest()
    }
}

@DisabledIfSystemProperty(named = "testReloadBaseConfiguration", matches = "true")
class RuntimeTest: dev.reformator.stacktracedecoroutinator.test.RuntimeTest() {
    @BeforeTest
    fun setup() {
        setupTest()
    }
}

// Jacoco only instruments this class
@DisabledIfSystemProperty(named = "testReloadBaseConfiguration", matches = "true")
class JacocoInstrumentedMethodTest {
    @BeforeTest
    fun setup() {
        setupTest()
    }

    @Test
    fun jacocoInstrumentedMethodTest(): Unit = runBlocking {
        suspend fun jacocoInstrumentedMethod() {
            yield()
            yield()
        }

        jacocoInstrumentedMethod()
    }
}

@EnabledIfSystemProperty(named = "testReloadBaseConfiguration", matches = "true")
class ReloadBaseContinuationTest {
    @Test
    fun reloadBaseContinuation() {
        val baseContinuationClass = Class.forName(BASE_CONTINUATION_CLASS_NAME)
        assertFalse(baseContinuationClass.isDecoroutinatorBaseContinuation)
        DecoroutinatorRuntime.load()
        assertTrue(baseContinuationClass.isDecoroutinatorBaseContinuation)
    }
}
