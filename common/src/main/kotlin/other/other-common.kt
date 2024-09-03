@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.commonother

object SelfCalledSuspendLambda: suspend (suspend () -> Any?) -> Any? {
    override suspend fun invoke(argumentLambda: suspend () -> Any?): Any? =
        argumentLambda()
}
