@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.plugins

import dev.reformator.bytecodeprocessor.pluginapi.ProcessingDirectory
import dev.reformator.bytecodeprocessor.pluginapi.Processor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodInsnNode
import java.util.*
import kotlin.jvm.internal.Intrinsics
import kotlin.reflect.KFunction

class RemoveKotlinStdlibProcessor(
    private val includeClassNames: Set<Regex>? = null,
    private val includeModuleInfo: Boolean = true
): Processor {
    override fun process(directory: ProcessingDirectory) {
        directory.classes.forEach { clazz ->
            if (includeClassNames != null) {
                val className = Type.getObjectType(clazz.node.name).className
                if (includeClassNames.all { !it.matches(className) }) {
                    return@forEach
                }
            }
            clazz.node.methods?.forEach { method ->
                method.instructions?.forEach { instruction ->
                    if (instruction is MethodInsnNode) {
                        if (
                            instruction.opcode == Opcodes.INVOKESTATIC
                            && instruction.owner == Type.getInternalName(Intrinsics::class.java)
                            && instruction.name in intrinsicCheckNotNullWithMessageMethodNames
                            && instruction.desc == "(${Type.getDescriptor(Object::class.java)}${Type.getDescriptor(String::class.java)})V"
                        ) {
                            instruction.owner = Type.getInternalName(Objects::class.java)
                            instruction.name = run { val x: (Any, String) -> Any = Objects::requireNonNull; x as KFunction<*> }.name
                            instruction.desc = "(${Type.getDescriptor(Object::class.java)}${Type.getDescriptor(String::class.java)})${Type.getDescriptor(Object::class.java)}"
                            method.instructions.insert(instruction, InsnNode(Opcodes.POP))
                            clazz.markModified()
                        } else if (
                            instruction.opcode == Opcodes.INVOKESTATIC
                            && instruction.owner == Type.getInternalName(Intrinsics::class.java)
                            && instruction.name in intrinsicThrowWithMessageMethodNames
                            && instruction.desc == "(${Type.getDescriptor(String::class.java)})V"
                        ) {
                            method.instructions.insertBefore(instruction, InsnList().apply {
                                add(InsnNode(Opcodes.ACONST_NULL))
                                add(InsnNode(Opcodes.SWAP))
                            })
                            instruction.owner = Type.getInternalName(Objects::class.java)
                            instruction.name = run { val x: (Any, String) -> Any = Objects::requireNonNull; x as KFunction<*> }.name
                            instruction.desc = "(${Type.getDescriptor(Object::class.java)}${Type.getDescriptor(String::class.java)})${Type.getDescriptor(Object::class.java)}"
                            method.instructions.insert(instruction, InsnNode(Opcodes.POP))
                            clazz.markModified()
                        } else if (
                            instruction.opcode == Opcodes.INVOKESTATIC
                            && instruction.owner == Type.getInternalName(Intrinsics::class.java)
                            && instruction.name == run { val x: (Any) -> Unit = Intrinsics::checkNotNull; x as KFunction<*> }.name
                            && instruction.desc == "(${Type.getDescriptor(Object::class.java)})${Type.VOID_TYPE.descriptor}"
                        ) {
                            instruction.owner = Type.getInternalName(Objects::class.java)
                            instruction.name = run { val x: (Any) -> Any = Objects::requireNonNull; x as KFunction<*> }.name
                            instruction.desc = "(${Type.getDescriptor(Object::class.java)})${Type.getDescriptor(Object::class.java)}"
                            method.instructions.insert(instruction, InsnNode(Opcodes.POP))
                            clazz.markModified()
                        }
                    }
                }
            }
        }
        if (includeModuleInfo) {
            directory.module?.let { module ->
                if (module.node.requires?.removeIf { it.module == KOTLIN_STDLIB_MODULE } == true) {
                    module.markModified()
                }
            }
        }
    }
}

private const val KOTLIN_STDLIB_MODULE = "kotlin.stdlib"

private val intrinsicCheckNotNullWithMessageMethodNames = setOf(
    run {val x: (Any?, String) -> Unit = Intrinsics::checkNotNull; x as KFunction<*> }.name,
    Intrinsics::checkExpressionValueIsNotNull.name,
    Intrinsics::checkNotNullExpressionValue.name,
    run {val x: (Any?, String) -> Unit = Intrinsics::checkReturnedValueIsNotNull; x as KFunction<*> }.name,
    run {val x: (Any?, String) -> Unit = Intrinsics::checkFieldIsNotNull; x as KFunction<*> }.name,
    Intrinsics::checkParameterIsNotNull.name,
    Intrinsics::checkNotNullParameter.name
)

private val intrinsicThrowWithMessageMethodNames = setOf(
    run {val x: (String) -> Unit = Intrinsics::throwNpe; x as KFunction<*>}.name,
    run {val x: (String) -> Unit = Intrinsics::throwJavaNpe; x as KFunction<*>}.name,
    Intrinsics::throwUninitializedProperty.name,
    Intrinsics::throwUninitializedPropertyAccessException.name,
    run {val x: (String) -> Unit = Intrinsics::throwAssert; x as KFunction<*>}.name,
    run {val x: (String) -> Unit = Intrinsics::throwIllegalArgument; x as KFunction<*>}.name,
    run {val x: (String) -> Unit = Intrinsics::throwIllegalState; x as KFunction<*>}.name,
    run {val x: (String) -> Unit = Intrinsics::throwUndefinedForReified; x as KFunction<*>}.name,
    run {val x: (String) -> Unit = Intrinsics::needClassReification; x as KFunction<*>}.name,
)
