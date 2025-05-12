import dev.reformator.stacktracedecoroutinator.common.internal.BASE_CONTINUATION_CLASS_NAME
import dev.reformator.stacktracedecoroutinator.jvm.DecoroutinatorJvmApi
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorTransformed
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.condition.DisabledIfSystemProperty
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        assertFalse(baseContinuationClass.isTransformed)
        DecoroutinatorJvmApi.install()
        assertTrue(baseContinuationClass.isTransformed)
    }
}

@DisabledIfSystemProperty(named = "testReloadBaseConfiguration", matches = "true")
class TailCallDeoptimizeTest: dev.reformator.stacktracedecoroutinator.test.TailCallDeoptimizeTest() {
    @BeforeTest
    fun setup() {
        setupTest()
    }

    @Test
    fun localBasic() {
        basic()
    }

    @Test
    fun localInterfaceWithDefaultMethodImpl() {
        interfaceWithDefaultMethodImpl()
    }
}

@DisabledIfSystemProperty(named = "testReloadBaseConfiguration", matches = "true")
class CustomClassLoaderTest: dev.reformator.stacktracedecoroutinator.test.CustomClassLoaderTest() {
    @BeforeTest
    fun setup() {
        setupTest()
    }
}

@DisabledIfSystemProperty(named = "testReloadBaseConfiguration", matches = "true")
class JvmTest: dev.reformator.stacktracedecoroutinator.testjvm.JvmTest() {

    @BeforeTest
    fun setup() {
        setupTest()
    }

    @Test
    fun check() {
        `start class with spaces`(true)
    }
}

val Class<*>.isTransformed: Boolean
    get() {
        val transformed = getDeclaredAnnotation(DecoroutinatorTransformed::class.java) ?: return false
        return !transformed.skipSpecMethods
    }

fun setupTest() {
    System.setProperty(
        "dev.reformator.stacktracedecoroutinator.jvmAgentDebugMetadataInfoResolveStrategy",
        "SYSTEM_RESOURCE"
    )
    DecoroutinatorJvmApi.install()
}
