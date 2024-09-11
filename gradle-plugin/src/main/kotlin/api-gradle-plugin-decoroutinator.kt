@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("ApiGradlePluginDecoroutinatorKt")

package org.gradle.kotlin.dsl

import dev.reformator.bytecodeprocessor.intrinsics.LoadConstant
import dev.reformator.stacktracedecoroutinator.gradleplugin.DecoroutinatorPluginExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler

fun Project.stacktraceDecoroutinator(configure: DecoroutinatorPluginExtension.() -> Unit) {
    extensions.configure(::stacktraceDecoroutinator.name, configure)
}

@Suppress("UnusedReceiverParameter")
fun DependencyHandler.decoroutinatorCommon(): Any =
    "dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-common:$projectVersionIntrinsic"

@Suppress("UnusedReceiverParameter")
fun DependencyHandler.decoroutinatorProvider(): Any =
    "dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-provider:$projectVersionIntrinsic"

@Suppress("UnusedReceiverParameter")
fun DependencyHandler.decoroutinatorJvmRuntime(): Any =
    "dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-generator:$projectVersionIntrinsic"

@Suppress("UnusedReceiverParameter")
fun DependencyHandler.decoroutinatorAndroidRuntime(): Any =
    "dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-generator-android:$projectVersionIntrinsic"

private val projectVersionIntrinsic: String
    @LoadConstant get() { error("intrinsics failed") }
