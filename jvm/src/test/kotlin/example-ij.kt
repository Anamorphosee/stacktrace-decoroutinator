package dev.reformator.stacktracedecoroutinator.jvmtests

import dev.reformator.stacktracedecoroutinator.jvm.DecoroutinatorJvmApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

fun main() {
    DecoroutinatorJvmApi.install()
    runBlocking {
        try {
            withContext(Dispatchers.Default) {
                delay(1.seconds)
                doWork()
            }
        }
        catch (t: Throwable) {
            println("trace when caught:")
            t.printStackTrace(System.out)
        }
    }
}

private fun doWork(): Int {
    val ise = IllegalStateException("Internal invariant failed")
    println("trace when thrown:")
    ise.printStackTrace(System.out)
    throw ise
}
