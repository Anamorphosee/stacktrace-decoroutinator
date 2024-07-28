@file:Suppress("PackageDirectoryMismatch")

package org.gradle.kotlin.dsl

import dev.reformator.stacktracedecoroutinator.gradleplugin.DecoroutinatorPluginExtension
import dev.reformator.stacktracedecoroutinator.gradleplugin.EXTENSION_NAME
import org.gradle.api.Project

fun Project.stacktraceDecoroutinator(configure: DecoroutinatorPluginExtension.() -> Unit) {
    extensions.configure(EXTENSION_NAME, configure)
}
