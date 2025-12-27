@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("ApiGradlePluginDecoroutinatorKt")

package org.gradle.kotlin.dsl

import dev.reformator.bytecodeprocessor.intrinsics.LoadConstant
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.gradleplugin.ANDROID_CURRENT_PROGUARD_RULES_FILE_NAME
import dev.reformator.stacktracedecoroutinator.gradleplugin.ATTRIBUTE_EXTENSION_NAME
import dev.reformator.stacktracedecoroutinator.gradleplugin.DecoroutinatorAttributePluginExtension
import dev.reformator.stacktracedecoroutinator.gradleplugin.DecoroutinatorPluginExtension
import dev.reformator.stacktracedecoroutinator.gradleplugin.EXTENSION_NAME
import dev.reformator.stacktracedecoroutinator.gradleplugin.decoroutinatorDir
import org.gradle.api.Project
import java.io.File

fun Project.stacktraceDecoroutinator(configure: DecoroutinatorPluginExtension.() -> Unit) {
    extensions.configure(EXTENSION_NAME, configure)
}

val Project.stacktraceDecoroutinator: DecoroutinatorPluginExtension
    get() = extensions.getByName(EXTENSION_NAME) as DecoroutinatorPluginExtension

@Suppress("unused")
fun Project.stacktraceDecoroutinatorAttribute(configure: DecoroutinatorAttributePluginExtension.() -> Unit) {
    extensions.configure(ATTRIBUTE_EXTENSION_NAME, configure)
}

@Suppress("unused")
val Project.stacktraceDecoroutinatorAttribute: DecoroutinatorAttributePluginExtension
    get() = extensions.getByName(ATTRIBUTE_EXTENSION_NAME) as DecoroutinatorAttributePluginExtension

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
