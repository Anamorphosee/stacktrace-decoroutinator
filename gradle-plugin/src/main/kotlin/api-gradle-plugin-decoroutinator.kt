@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("ApiGradlePluginDecoroutinatorKt")

package org.gradle.kotlin.dsl

import dev.reformator.bytecodeprocessor.intrinsics.LoadConstant
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.gradleplugin.DecoroutinatorPluginExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import java.io.File

fun Project.stacktraceDecoroutinator(configure: DecoroutinatorPluginExtension.() -> Unit) {
    extensions.configure(::stacktraceDecoroutinator.name, configure)
}

@Suppress("UnusedReceiverParameter")
fun DependencyHandler.decoroutinatorCommon(): Any =
    "dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-common:$projectVersionIntrinsic"

@Suppress("UnusedReceiverParameter")
fun DependencyHandler.decoroutinatorJvmRuntime(): Any =
    "dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-generator:$projectVersionIntrinsic"

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
val Project.decoroutinatorProGuardRules: File
    get() {
        val result = layout.buildDirectory.dir("decoroutinator").get().file("rules.pro").asFile
        if (result.isFile) {
            return result
        }
        result.parentFile.mkdirs()
        result.writeText(PROGUARD_RULES)
        return result
    }

private val projectVersionIntrinsic: String
    @LoadConstant get() { fail() }

private val PROGUARD_RULES = """
    # Decoroutinator ProGuard rules
    -keep @kotlin.coroutines.jvm.internal.DebugMetadata class * { int label; }
    -keep @kotlin.coroutines.jvm.internal.DebugMetadata interface * { int label; }
    -keep @kotlin.coroutines.jvm.internal.DebugMetadata enum * { int label; }
    -keep @dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorTransformed class * {
        static *(dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec, java.lang.Object);
    }
    -keep @dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorTransformed interface * {
        static *(dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec, java.lang.Object);
    }
    -keep @dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorTransformed enum * {
        static *(dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec, java.lang.Object);
    }
    -keep @dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorApi class * { *; }
    -keep @dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorApi interface * { *; }
    -keep @dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorApi enum * { *; }
    
""".trimIndent()
