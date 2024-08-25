@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.intrinsics

import kotlin.reflect.KClass

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
annotation class ChangeClassName(
    val to: KClass<*> = `no class`::class,
    val toName: String = NO_NAME,
    val deleteAfterChanging: Boolean = false
)
