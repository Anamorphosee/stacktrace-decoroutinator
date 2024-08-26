@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.runtime

import dev.reformator.stacktracedecoroutinator.jvm.DecoroutinatorJvmApi

object DecoroutinatorRuntime {
    @Deprecated(
        message = "Please replace with a new API.",
        replaceWith = ReplaceWith(
            expression = "DecoroutinatorJvmApi.install()",
            imports = ["dev.reformator.stacktracedecoroutinator.jvm.DecoroutinatorJvmApi"]
        )
    )
    fun load() {
        DecoroutinatorJvmApi.install()
    }
}
