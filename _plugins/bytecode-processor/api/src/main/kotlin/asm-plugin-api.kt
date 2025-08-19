@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.pluginapi

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.ModuleNode

interface ProcessingClass {
    val node: ClassNode
    fun markModified()
    fun delete()
}

interface ProcessingModule {
    val node: ModuleNode
    fun markModified()
}

interface ProcessingDirectory {
    val classes: Sequence<ProcessingClass>
    val module: ProcessingModule?
}

interface BytecodeProcessorContext {
    interface Key<T: Any> {
        val id: String
        val default: T
        fun merge(value1: T, value2: T): T
    }

    operator fun <T: Any> get(key: Key<T>): T

    fun <T: Any> merge(key: Key<T>, value: T): T
}

@JvmInline
value class BytecodeProcessorContextImpl private constructor(
    val values: MutableMap<String, Any>
): BytecodeProcessorContext {
    constructor(): this(hashMapOf())

    @Suppress("UNCHECKED_CAST")
    override fun <T: Any> get(key: BytecodeProcessorContext.Key<T>): T =
        values[key.id]?.let { it as T } ?: key.default

    @Suppress("UNCHECKED_CAST")
    override fun <T: Any> merge(key: BytecodeProcessorContext.Key<T>, value: T): T =
        values.merge(key.id, value) { value1, value2 ->
            key.merge(value1 as T, value2 as T)
        } as T
}

interface Processor {
    fun process(directory: ProcessingDirectory, context: BytecodeProcessorContext)

    val usedContextKeys: Collection<BytecodeProcessorContext.Key<*>>
}
