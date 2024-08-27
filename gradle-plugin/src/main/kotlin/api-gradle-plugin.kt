@file:Suppress("PackageDirectoryMismatch")

package org.gradle.kotlin.dsl

import dev.reformator.stacktracedecoroutinator.gradleplugin.DecoroutinatorPluginExtension
import org.gradle.api.Project

fun Project.stacktraceDecoroutinator(configure: DecoroutinatorPluginExtension.() -> Unit) {
    extensions.configure(::stacktraceDecoroutinator.name, configure)
}
