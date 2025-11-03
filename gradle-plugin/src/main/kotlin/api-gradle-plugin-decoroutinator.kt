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
import java.io.File

fun Project.stacktraceDecoroutinator(configure: DecoroutinatorPluginExtension.() -> Unit) {
    extensions.configure(::stacktraceDecoroutinator.name, configure)
}

fun Project.stacktraceDecoroutinatorAttribute(configure: DecoroutinatorAttributePluginExtension.() -> Unit) {
    extensions.configure(::stacktraceDecoroutinatorAttribute.name, configure)
}

@Suppress("unused")
@Deprecated("replace with method", replaceWith = ReplaceWith("decoroutinatorAndroidProGuardRules()"))
val Project.decoroutinatorAndroidProGuardRules: File
    get() = decoroutinatorAndroidProGuardRules()

fun Project.decoroutinatorAndroidProGuardRules(): File =
    decoroutinatorDir.resolve(ANDROID_CURRENT_PROGUARD_RULES_FILE_NAME)

fun decoroutinatorCommon(): Any =
    "dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-common:$projectVersionIntrinsic"

fun decoroutinatorJvmRuntime(): Any =
    "dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-generator-jvm:$projectVersionIntrinsic"

fun decoroutinatorAndroidRuntime(): Any =
    "dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-generator-android:$projectVersionIntrinsic"

fun decoroutinatorRegularMethodHandleInvoker(): Any =
    "dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-mh-invoker:$projectVersionIntrinsic"

fun decoroutinatorAndroidMethodHandleInvoker(): Any =
    "dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-mh-invoker-android:$projectVersionIntrinsic"

fun decoroutinatorJvmMethodHandleInvoker(): Any =
    "dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-mh-invoker-jvm:$projectVersionIntrinsic"

fun decoroutinatorRuntimeSettings(): Any =
    "dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-runtime-settings:$projectVersionIntrinsic"

private val projectVersionIntrinsic: String
    @LoadConstant("version") get() { fail() }
