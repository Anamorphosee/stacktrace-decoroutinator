import dev.reformator.stacktracedecoroutinator.gradlepluginandroidtests.TestLocalFile
import org.junit.Test

class AccessTest {
    @Test
    fun accessFileInfMainModule() {
        TestLocalFile::class.java.name
    }
}