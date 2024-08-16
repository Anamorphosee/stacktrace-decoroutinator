package dev.reformator.stacktracedecoroutinator.provider.stdlib

import dev.reformator.stacktracedecoroutinator.provider.internal.DecoroutinatorProvider
import java.lang.invoke.MethodHandles

@Target(AnnotationTarget.CLASS)
@Retention
annotation class DecoroutinatorMarker

val isEnabled: Boolean
    get() = DecoroutinatorProvider.instance.isEnabled

val isPrepared: Boolean
    get() = DecoroutinatorProvider.instance.isPrepared

fun prepare(lookup: MethodHandles.Lookup) {
    DecoroutinatorProvider.instance.prepare(lookup)
}

fun awake(baseContinuation: Any, result: Any?) {
    DecoroutinatorProvider.instance.awake(baseContinuation, result)
}

