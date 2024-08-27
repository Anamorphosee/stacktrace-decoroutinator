@file:Suppress("PackageDirectoryMismatch")
@file:JvmName(GET_CURRENT_LINE_NUMBER_INTRINSIC_CLASS)

package dev.reformator.bytecodeprocessor.intrinsics

val currentLineNumber: Int
    @JvmName(GET_CURRENT_LINE_NUMBER_METHOD_NAME) get() { fail() }

const val GET_CURRENT_LINE_NUMBER_METHOD_NAME = "getCurrentLineNumber"
private const val GET_CURRENT_LINE_NUMBER_INTRINSIC_CLASS = "GetCurrentLineNumberIntrinsicsKt"
val getCurrentLineNumberIntrinsicClassName =
    "${_getCurrentLineNumberPackage::class.java.packageName}.$GET_CURRENT_LINE_NUMBER_INTRINSIC_CLASS"

@Suppress("ClassName")
private class _getCurrentLineNumberPackage
