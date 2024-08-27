@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.plugins

import dev.reformator.bytecodeprocessor.pluginapi.ProcessingDirectory
import dev.reformator.bytecodeprocessor.pluginapi.Processor

class RemoveModuleRequiresProcessor(
    private val moduleNames: Set<String>
): Processor {
    constructor(vararg moduleNames: String) : this(moduleNames.toSet())

    override fun process(directory: ProcessingDirectory) {
        if (directory.module?.node?.requires?.removeIf { it.module in moduleNames } == true) {
            directory.module!!.markModified()
        }
    }
}
