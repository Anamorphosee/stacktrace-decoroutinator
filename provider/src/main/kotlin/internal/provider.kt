@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.provider.internal

import java.lang.invoke.MethodHandles
import java.util.*

interface DecoroutinatorProvider {
    val isDecoroutinatorEnabled: Boolean
    val isBaseContinuationPrepared: Boolean
    fun prepareBaseContinuation(lookup: MethodHandles.Lookup)
    fun awakeBaseContinuation(baseContinuation: Any, result: Any?)
    fun registerTransformedClass(lookup: MethodHandles.Lookup)
}

val provider: DecoroutinatorProvider = ServiceLoader.load(DecoroutinatorProvider::class.java).iterator().next()
