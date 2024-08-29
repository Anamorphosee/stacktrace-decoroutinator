@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.intrinsics

import kotlin.reflect.KClass

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
annotation class DeleteInterface(
    val value: KClass<*>
)
