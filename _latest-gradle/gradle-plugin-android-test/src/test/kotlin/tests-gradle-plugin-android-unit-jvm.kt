import dev.reformator.stacktracedecoroutinator.gradlepluginandroidtests.TestLocalFile
import org.junit.Test

class AccessTest {
    @Test
    fun accessFileInMainModule() {
        TestLocalFile::class.java.name
    }
}