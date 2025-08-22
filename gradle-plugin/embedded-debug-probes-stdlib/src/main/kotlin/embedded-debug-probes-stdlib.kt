@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("DebugProbesKt")

package kotlin.coroutines.jvm.internal

import java.util.ServiceLoader
import kotlin.coroutines.Continuation

interface DecoroutinatorDebugProbesProvider {
    fun <T> probeCoroutineCreated(completion: Continuation<T>): Continuation<T>
    fun probeCoroutineResumed(frame: Continuation<*>)
    fun probeCoroutineSuspended(frame: Continuation<*>)
}

private val provider: DecoroutinatorDebugProbesProvider =
    ServiceLoader.load(DecoroutinatorDebugProbesProvider::class.java).iterator().next()

@Suppress("unused")
fun <T> probeCoroutineCreated(completion: Continuation<T>): Continuation<T> =
    provider.probeCoroutineCreated(completion)

@Suppress("unused")
fun probeCoroutineResumed(frame: Continuation<*>) {
    provider.probeCoroutineResumed(frame)
}

@Suppress("unused")
fun probeCoroutineSuspended(frame: Continuation<*>) {
    provider.probeCoroutineSuspended(frame)
}
