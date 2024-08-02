![Maven Central](https://img.shields.io/maven-central/v/dev.reformator.stacktracedecoroutinator/stacktrace-decoroutinator-jvm)
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
- https://github.com/arrow-kt/arrow/issues/2647
- https://stackoverflow.com/questions/54349418/how-to-recover-the-coroutines-true-call-trace
- https://stackoverflow.com/questions/69226016/how-to-get-full-exception-stacktrace-when-using-await-on-completablefuture

The Kotlin team are known about the problem and has come up with a [solution](https://github.com/Kotlin/kotlinx.coroutines/blob/master/docs/topics/debugging.md#stacktrace-recovery), but it solves just a part of the cases.
For example, the exception from the example above still lacks some calls.

### Solution
Stacktrace-decoroutinator replaces the coroutine awakening implementation.

It generates methods at runtime with names that match the entire coroutine call stack.

These methods don't do anything except call each other in the coroutine call stack order.

Thus, if the coroutine throws an exception, they mimic the real call stack of the coroutine during the creation of the exception stacktrace.

### JVM
There are three ways to enable Stacktrace-decoroutinator for JVM.
1. If you build your project with Gradle, just apply Gradle plugin with id `dev.reformator.stracktracedecoroutinator`.
2. Add dependency `dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-jvm:2.4.0` and call method `DecoroutinatorRuntime.load()`.
3. Add `-javaagent:stacktrace-decoroutinator-jvm-agent-2.4.0.jar` to your JVM start arguments. Corresponding dependency is `dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-jvm-agent:2.4.0`.

The first option generates auxiliary methods at build time and the other two use the Java instrumentation API at runtime. 

Usage example:
```kotlin
import dev.reformator.stacktracedecoroutinator.runtime.DecoroutinatorRuntime
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

suspend fun rec(depth: Int) {
    if (depth == 0) {
        yield()
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
prints out:
```
java.lang.Exception: exception at 1722597709832
	at ExampleKt.rec(example.kt:8)
	at ExampleKt$rec$1.invokeSuspend(example.kt)
	at kotlin.coroutines.jvm.internal.JavaUtilsImpl$1.apply(JavaUtilsImpl.java:52)
	at ExampleKt.rec(example.kt:10)
	at ExampleKt.rec(example.kt:10)
	at ExampleKt.rec(example.kt:10)
	at ExampleKt.rec(example.kt:10)
	at ExampleKt.rec(example.kt:10)
	at ExampleKt.rec(example.kt:10)
	at ExampleKt.rec(example.kt:10)
	at ExampleKt.rec(example.kt:10)
	at ExampleKt.rec(example.kt:10)
	at ExampleKt.rec(example.kt:10)
	at ExampleKt$main$1.invokeSuspend(example.kt:17)
	at dev.reformator.stacktracedecoroutinator.runtime.AwakenerKt.awake(awakener.kt:93)
	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(basecontinuation.kt:20)
	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:104)
	at kotlinx.coroutines.EventLoopImplBase.processNextEvent(EventLoop.common.kt:277)
	at kotlinx.coroutines.BlockingCoroutine.joinBlocking(Builders.kt:95)
	at kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking(Builders.kt:69)
	at kotlinx.coroutines.BuildersKt.runBlocking(Unknown Source)
	at kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking$default(Builders.kt:48)
	at kotlinx.coroutines.BuildersKt.runBlocking$default(Unknown Source)
	at ExampleKt.main(example.kt:16)
	at ExampleKt.main(example.kt)
```

### Android
For Android there is only one option to enable Stacktrace-decoroutinator - apply the Gradle plugin `dev.reformator.stacketracedecoroutinator` to your application's project.
```kotlin
plugins {
    id("dev.reformator.stacktracedecoroutinator") version "2.4.0"
}
```

### Using ProGuard
If you use ProGuard (usually for Android) please add the following exclusion rules:
```
-keep @kotlin.coroutines.jvm.internal.DebugMetadata class * { *; }
-keep @dev.reformator.stacktracedecoroutinator.runtime.DecoroutinatorTransformed class * { *; }
```

### Problem with Jacoco and Decoroutinator
Using Jacoco and Decoroutinator as a Java agent may lead to the loss of code coverage. It's [common Jacoco Problem](https://www.eclemma.org/jacoco/trunk/doc/classids.html). In order not to lose coverage, make sure that the Jacoco agent comes before the Decoroutinator agent. See more at https://github.com/Anamorphosee/stacktrace-decoroutinator/issues/24.

### Communication
Feel free to ask any question at [Discussions](https://github.com/Anamorphosee/stacktrace-decoroutinator/discussions).
