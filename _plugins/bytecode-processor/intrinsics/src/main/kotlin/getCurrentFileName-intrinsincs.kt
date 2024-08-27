@file:Suppress("PackageDirectoryMismatch")
@file:JvmName(GET_CURRENT_FILE_NAME_INTRINSIC_CLASS)

package dev.reformator.bytecodeprocessor.intrinsics

val currentFileName: String
    @JvmName(GET_CURRENT_FILE_NAME_METHOD_NAME) get() { fail() }

const val GET_CURRENT_FILE_NAME_METHOD_NAME = "getCurrentFileName"
private const val GET_CURRENT_FILE_NAME_INTRINSIC_CLASS = "GetCurrentFileNameIntrinsicsKt"
val getCurrentFileNameIntrinsicClassName =
    "${_getCurrentFileNamePackage::class.java.packageName}.$GET_CURRENT_FILE_NAME_INTRINSIC_CLASS"

@Suppress("ClassName")
private class _getCurrentFileNamePackage
