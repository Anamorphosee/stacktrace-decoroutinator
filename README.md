[![Maven Central](https://img.shields.io/maven-central/v/dev.reformator.stacktracedecoroutinator/stacktrace-decoroutinator-jvm.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22dev.reformator.stacktracedecoroutinator%22%20AND%20a:%22stacktrace-decoroutinator-jvm%22)
# Stacktrace-decoroutinator
Library for recovering stack trace in exceptions thrown in Kotlin coroutines.

Supports JVM 1.8 or higher and Android API 26 or higher.

###JVM

To enable Stacktrace-decoroutinator for JVM you should add dependency `stacktrace-decoroutinator-jvm` and call method `DecoroutinatorRuntime.load()`.

Usage example:
```kotlin
import dev.reformator.stacktracedecoroutinator.runtime.DecoroutinatorRuntime
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

suspend fun rec(depth: Int) {
    if (depth == 0) {
        delay(100)
        throw Exception("exception at ${System.currentTimeMillis()}")
    }
    rec(depth - 1)
}

fun main() {
    DecoroutinatorRuntime.load() // enable stacktrace-decoroutinator runtime

    try {
        runBlocking {
            rec(10)
        }
    } catch (e: Exception) {
        e.printStackTrace() // print full stack trace with 10 recursive calls
    }
}
```

###Android

To enable Stacktrace-decoroutinator for Android you should add dependency `stacktrace-decoroutinator-android` in your Android application.

If you override `android:name` attribute for the application in your `AndroidManifest.xml` then your should call method `DecoroutinatorRuntime.load()`  in `Application.onCreate()` method.
