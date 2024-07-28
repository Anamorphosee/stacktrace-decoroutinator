@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.generator

import dev.reformator.stacktracedecoroutinator.runtime.BaseDecoroutinatorRegistry
import dev.reformator.stacktracedecoroutinator.runtime.MethodHandleRegistry

class GeneratorDecoroutinatorRegistry: BaseDecoroutinatorRegistry() {
    override val methodHandleRegistry: MethodHandleRegistry
        get() = GeneratorMethodHandleRegistry
}
