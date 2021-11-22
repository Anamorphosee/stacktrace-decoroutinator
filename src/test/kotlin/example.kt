import dev.reformator.stacktracedecoroutinator.util.DocoroutinatorRuntime
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

suspend fun rec(depth: Int) {
    if (depth == 0) {
        delay(100)
        throw Exception("exception in ${System.currentTimeMillis()}")
    }
    rec(depth - 1)
}

fun main() {
    DocoroutinatorRuntime().enableDecoroutinatorRuntime() // enable stacktrace-decoroutinator runtime

    try {
        runBlocking {
            rec(10)
        }
    } catch (e: Exception) {
        e.printStackTrace() // print full stack trace with 10 recursive calls
    }
}
