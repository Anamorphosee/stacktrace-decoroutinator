@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("ApiGradlePluginDecoroutinatorKt")

package org.gradle.kotlin.dsl

import dev.reformator.bytecodeprocessor.intrinsics.LoadConstant
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.gradleplugin.ANDROID_CURRENT_PROGUARD_RULES_FILE_NAME
import dev.reformator.stacktracedecoroutinator.gradleplugin.DecoroutinatorAttributePluginExtension
import dev.reformator.stacktracedecoroutinator.gradleplugin.DecoroutinatorPluginExtension
import dev.reformator.stacktracedecoroutinator.gradleplugin.decoroutinatorDir
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import java.io.File

fun Project.stacktraceDecoroutinator(configure: DecoroutinatorPluginExtension.() -> Unit) {
    extensions.configure(::stacktraceDecoroutinator.name, configure)
}

fun Project.stacktraceDecoroutinatorAttribute(configure: DecoroutinatorAttributePluginExtension.() -> Unit) {
    extensions.configure(::stacktraceDecoroutinatorAttribute.name, configure)
}

@Suppress("UnusedReceiverParameter")
fun DependencyHandler.decoroutinatorCommon(): Any =
    "dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-common:$projectVersionIntrinsic"

@Suppress("UnusedReceiverParameter")
fun DependencyHandler.decoroutinatorJvmRuntime(): Any =
    "dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-generator-jvm:$projectVersionIntrinsic"

@Suppress("UnusedReceiverParameter")
fun DependencyHandler.decoroutinatorAndroidRuntime(): Any =
    "dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-generator-android:$projectVersionIntrinsic"

@Suppress("UnusedReceiverParameter")
fun DependencyHandler.decoroutinatorRegularMethodHandleInvoker(): Any =
    "dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-mh-invoker:$projectVersionIntrinsic"

@Suppress("UnusedReceiverParameter")
fun DependencyHandler.decoroutinatorAndroidMethodHandleInvoker(): Any =
    "dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-mh-invoker-android:$projectVersionIntrinsic"

@Suppress("UnusedReceiverParameter")
fun DependencyHandler.decoroutinatorJvmMethodHandleInvoker(): Any =
    "dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-mh-invoker-jvm:$projectVersionIntrinsic"

@Suppress("UnusedReceiverParameter")
fun DependencyHandler.decoroutinatorRuntimeSettings(): Any =
    "dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-runtime-settings:$projectVersionIntrinsic"

@Suppress("unused")
val Project.decoroutinatorAndroidProGuardRules: File
    get() = decoroutinatorDir.resolve(ANDROID_CURRENT_PROGUARD_RULES_FILE_NAME)

private val projectVersionIntrinsic: String
    @LoadConstant("version") get() { fail() }
