@file:Suppress("PackageDirectoryMismatch")
@file:JvmName(GET_GRADLE_PROJECT_VERSION_INTRINSICS_CLASS_NAME)

package dev.reformator.bytecodeprocessor.intrinsics

const val GET_GRADLE_PROJECT_VERSION_METHOD_NAME = "getGradleProjectVersion"
private const val GET_GRADLE_PROJECT_VERSION_INTRINSICS_CLASS_NAME = "GetGradleProjectVersionIntrinsicsKt"

val gradleProjectVersion: String
    @JvmName(GET_GRADLE_PROJECT_VERSION_METHOD_NAME) get() { fail() }

val getGradleProjectVersionClassName = "${_getGradleProjectVersionPackageMarker::class.java.packageName}.$GET_GRADLE_PROJECT_VERSION_INTRINSICS_CLASS_NAME"

@Suppress("ClassName")
private class _getGradleProjectVersionPackageMarker
