[![Maven Central](https://img.shields.io/maven-central/v/dev.reformator.stacktracedecoroutinator/stacktrace-decoroutinator-jvm.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22dev.reformator.stacktracedecoroutinator%22%20AND%20a:%22stacktrace-decoroutinator-jvm%22)
[![Gitter](https://badges.gitter.im/stacktrace-decoroutinator/community.svg)](https://gitter.im/stacktrace-decoroutinator/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)
# Stacktrace-decoroutinator
Library for recovering stack trace in exceptions thrown in Kotlin coroutines.

Supports JVM 1.8 or higher and Android API 26 or higher.

### Motivation
Coroutines is a significant Kotlin feature which allows you to write asynchronous code in synchronous style.

It's absolutely perfect until you need to investigate problems in your code.

One of the common problems is the shortened stack trace in exceptions thrown in coroutines. For example, this code prints out the stack trace below:
```kotlin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

suspend fun fun1() {
    delay(10)
    throw Exception("exception at ${System.currentTimeMillis()}")
}

suspend fun fun2() {
    fun1()
    delay(10)
}

suspend fun fun3() {
    fun2()
    delay(10)
}

fun main() {
    try {
        runBlocking {
            fun3()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
```
```
java.lang.Exception: exception at 1641842199891
  at MainKt.fun1(main.kt:6)
  at MainKt$fun1$1.invokeSuspend(main.kt)
  at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
  at kotlinx.coroutines.DispatchedTaskKt.resume(DispatchedTask.kt:234)
  at kotlinx.coroutines.DispatchedTaskKt.dispatch(DispatchedTask.kt:166)
  at kotlinx.coroutines.CancellableContinuationImpl.dispatchResume(CancellableContinuationImpl.kt:397)
  at kotlinx.coroutines.CancellableContinuationImpl.resumeImpl(CancellableContinuationImpl.kt:431)
  at kotlinx.coroutines.CancellableContinuationImpl.resumeImpl$default(CancellableContinuationImpl.kt:420)
  at kotlinx.coroutines.CancellableContinuationImpl.resumeUndispatched(CancellableContinuationImpl.kt:518)
  at kotlinx.coroutines.EventLoopImplBase$DelayedResumeTask.run(EventLoop.common.kt:494)
  at kotlinx.coroutines.EventLoopImplBase.processNextEvent(EventLoop.common.kt:279)
  at kotlinx.coroutines.BlockingCoroutine.joinBlocking(Builders.kt:85)
  at kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking(Builders.kt:59)
  at kotlinx.coroutines.BuildersKt.runBlocking(Unknown Source)
  at kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking$default(Builders.kt:38)
  at kotlinx.coroutines.BuildersKt.runBlocking$default(Unknown Source)
  at MainKt.main(main.kt:21)
  at MainKt.main(main.kt)
```
The stack trace doesn't represent the true coroutine call stack: calls of functions `fun3` and `fun2` are absent.

In complex systems, even more calls may be missing. This can make debugging much more difficult.

Some examples of suffering from this problem:
- https://stackoverflow.com/questions/54349418/how-to-recover-the-coroutines-true-call-trace
- https://stackoverflow.com/questions/69226016/how-to-get-full-exception-stacktrace-when-using-await-on-completablefuture
- https://gitanswer.com/kotlinx-coroutines-stack-trace-recovery-kotlin-236882832

The Kotlin team are known about the problem and has come up with a [solution](https://github.com/Kotlin/kotlinx.coroutines/blob/master/docs/topics/debugging.md#stacktrace-recovery), but it solves just a part of the cases.
For example, the exception from the above example still lacks some calls.

### Solution
Stacktrace-decoroutinator replaces the coroutine awakening implementation.

It generates classes at runtime with names that match the entire coroutine call stack.

These classes don't do anything except call each other in the coroutine call stack order.

Thus, if the coroutine throws an exception, they mimic the real call stack of the coroutine during the creation of the exception stacktrace.

### JVM
To enable Stacktrace-decoroutinator for JVM you should add dependency `dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-jvm:2.2.1` and call method `DecoroutinatorRuntime.load()`.

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

### Android
To enable Stacktrace-decoroutinator for Android you should add dependency `dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-android:2.2.1` in your Android application and call method `DecoroutinatorRuntime.load()` before creating any coroutines.

It's recomended to add `DecoroutinatorRuntime.load()` call in your `Application.onCreate()` method. Example:
```kotlin
class MyApp: Application() {
    override fun onCreate() {
        DecoroutinatorRuntime.load()
        super.onCreate()
    }
}
```

### Communication
Feel free to ask any question at [Discussions](https://github.com/Anamorphosee/stacktrace-decoroutinator/discussions) or at [Gitter](https://gitter.im/stacktrace-decoroutinator/community).
