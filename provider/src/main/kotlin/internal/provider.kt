@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.provider.internal

import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorBaseContinuationAccessor
import java.lang.invoke.MethodHandles
import java.util.*

interface DecoroutinatorProvider {
    val isDecoroutinatorEnabled: Boolean
    val baseContinuationAccessor: DecoroutinatorBaseContinuationAccessor?
    fun prepareBaseContinuationAccessor(lookup: MethodHandles.Lookup): DecoroutinatorBaseContinuationAccessor
    fun awakeBaseContinuation(accessor: DecoroutinatorBaseContinuationAccessor, baseContinuation: Any, result: Any?)
    fun registerTransformedClass(lookup: MethodHandles.Lookup)
    fun getBaseContinuation(
        completion: Any?,
        fileName: String?,
        className: String,
        methodName: String,
        lineNumber: Int
    ): Any?
}

val provider: DecoroutinatorProvider = ServiceLoader.load(DecoroutinatorProvider::class.java).iterator().next()
