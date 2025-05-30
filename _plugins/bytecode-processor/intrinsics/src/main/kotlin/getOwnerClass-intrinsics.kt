@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.intrinsics

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
annotation class GetOwnerClass(
    val deleteAfterModification: Boolean = false
)

val ownerClass: Class<*>
    get() { fail() }

val ownerMethodName: String
    get() { fail() }
