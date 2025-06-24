@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.intrinsics

fun fail(): Nothing { error("intrinsic failed") }

@Suppress("ClassName") internal class noClass
internal const val NO_NAME = "\$no name\$"
