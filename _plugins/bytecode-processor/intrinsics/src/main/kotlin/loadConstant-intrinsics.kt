@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.intrinsics

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
annotation class LoadConstant(
    val key: String,
    val deleteAfterModification: Boolean = true
)

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
annotation class ClassNameConstant(val key: String)

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
annotation class MethodNameConstant(val key: String)
