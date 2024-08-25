@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.plugins.internal

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*

internal fun List<AnnotationNode>?.find(type: Class<out Annotation>): AnnotationNode? =
    this?.find { it.desc == Type.getDescriptor(type) }

internal fun AnnotationNode.getParameter(name: String): Any? {
    var index = 0
    while (index < values.orEmpty().size) {
        if (values[index] == name) return values[index + 1]
        index += 2
    }
    return null
}

internal fun AnnotationNode.setParameter(name: String, value: Any) {
    val parameters: MutableList<Any> = values ?: run { values = mutableListOf(); values }
    parameters.add(name)
    parameters.add(value)
}

internal val MethodNode.isStatic: Boolean
    get() = access and Opcodes.ACC_STATIC != 0

internal val String.internalName: String
    get() = replace('.', '/')

internal fun ClassNode.getOrCreateClinit(): MethodNode =
    methods?.firstOrNull {
        it.name == "<clinit>" && it.desc == "()V" && it.isStatic
    } ?: MethodNode(Opcodes.ASM9).apply {
        name = "<clinit>"
        desc = "()V"
        access = Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC
        instructions.add(InsnNode(Opcodes.RETURN))
        val methods: MutableList<MethodNode> = methods ?: run { methods = mutableListOf(); methods }
        methods.add(this)
    }

internal fun MethodNode.insertBefore(instructions: InsnList) {
    this.instructions.insertBefore(this.instructions.first, instructions)
}

internal fun MethodNode.insertBefore(instruction: AbstractInsnNode) {
    this.instructions.insertBefore(this.instructions.first, instruction)
}

internal inline fun <T> List<T>.forEachAllowingAddingToEnd(action: (T) -> Unit) {
    var index = 0
    while (index < size) {
        action(get(index++))
    }
}
