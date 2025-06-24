@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.intrinsics

import kotlin.reflect.KClass

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FILE)
annotation class ChangeClassName(
    val to: KClass<*> = noClass::class,
    val toName: String = NO_NAME,
    val deleteAfterChanging: Boolean = false
)
