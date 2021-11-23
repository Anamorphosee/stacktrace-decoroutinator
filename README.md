# Stacktrace-decoroutinator
Library for recovering stack trace in exceptions thrown in Kotlin coroutines.

Supports JVM(not Android) versions 1.8 or higher.

To enable it you should call method `DocoroutinatorRuntime.enableDecoroutinatorRuntime()` before creating any coroutine.

Usage example:
```kotlin
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
```
Available on [Maven Central](https://search.maven.org/artifact/dev.reformator.stacktracedecoroutinator/stacktrace-decoroutinator/1.0.0/jar)
