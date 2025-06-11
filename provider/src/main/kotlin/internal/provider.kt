@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.provider.internal

import java.lang.invoke.MethodHandles
import java.util.*

interface DecoroutinatorProvider {
    val isDecoroutinatorEnabled: Boolean
    val baseContinuationAccessor: BaseContinuationAccessor?
    fun prepareBaseContinuationAccessor(lookup: MethodHandles.Lookup): BaseContinuationAccessor
    fun awakeBaseContinuation(accessor: BaseContinuationAccessor, baseContinuation: Any, result: Any?)
    fun registerTransformedClass(lookup: MethodHandles.Lookup)
    fun getBaseContinuation(
        completion: Any?,
        fileName: String?,
        className: String,
        methodName: String,
        lineNumber: Int
    ): Any?
}

internal val provider: DecoroutinatorProvider = ServiceLoader.load(DecoroutinatorProvider::class.java).iterator().next()

interface BaseContinuationAccessor {
    fun invokeSuspend(baseContinuation: Any, result: Any?): Any?
    fun releaseIntercepted(baseContinuation: Any)
}

fun interface BaseContinuationAccessorProvider {
    fun createAccessor(lookup: MethodHandles.Lookup): BaseContinuationAccessor
}

@Target(AnnotationTarget.CLASS, AnnotationTarget.FILE)
@Retention(AnnotationRetention.BINARY)
annotation class AndroidKeep

@Target(AnnotationTarget.CLASS, AnnotationTarget.FILE)
@Retention(AnnotationRetention.BINARY)
annotation class AndroidLegacyKeep
