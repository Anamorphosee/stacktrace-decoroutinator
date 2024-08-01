@file:Suppress("PackageDirectoryMismatch")

package printstacktracemethod

import dev.reformator.stacktracedecoroutinator.generator.buildStacktraceMethodNode
import dev.reformator.stacktracedecoroutinator.runtime.registerTransformedFunctionClass
import dev.reformator.stacktracedecoroutinator.runtime.registerTransformedFunctionName
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import org.objectweb.asm.util.TraceClassVisitor
import java.io.PrintWriter
import java.lang.invoke.MethodHandles

fun main() {
    val classNode = ClassNode(Opcodes.ASM9).apply {
        version = Opcodes.V1_8
        access = Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER
        name = "dev/reformator/stacktracedecoroutinator/jvmcommon/StacktraceMethodPrinter"
        superName = Type.getInternalName(java.lang.Object::class.java)
        sourceFile = "unknown.kt"
    }
    classNode.methods = mutableListOf(buildStacktraceMethodNode(
        methodName = "stacktraceMethod",
        lineNumbers = setOf(10, 20, 25, 30),
        makePrivate = true
    ))

    val clinit = classNode.getOrCreateClinitMethod()
    val tryStart = LabelNode()
    val tryEnd = LabelNode()
    val tryHandler = LabelNode()
    clinit.instructions.insertBefore(
        clinit.instructions.first,
        buildCallRegisterLookupInstructions(
            tryStart = tryStart,
            tryEnd = tryEnd,
            tryHandler = tryHandler
        )
    )
    clinit.tryCatchBlocks = clinit.tryCatchBlocks.orEmpty().toMutableList().also { blocks ->
        blocks.add(TryCatchBlockNode(
            tryStart, tryEnd, tryHandler, Type.getInternalName(NoClassDefFoundError::class.java)
        ))
    }

    val classWriter = ClassWriter(ClassWriter.COMPUTE_FRAMES)
    classNode.accept(classWriter)
    val classReader = ClassReader(classWriter.toByteArray())

    PrintWriter(System.out.writer()).use {
        classReader.accept(TraceClassVisitor(it), 0)
    }
}

private fun ClassNode.getOrCreateClinitMethod(): MethodNode =
    methods.firstOrNull {
        it.name == "<clinit>" && it.desc == "()V" && it.isStatic
    } ?: MethodNode(Opcodes.ASM9).apply {
        name = "<clinit>"
        desc = "()V"
        access = Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC
        instructions.add(InsnNode(Opcodes.RETURN))
        methods.add(this)
    }

private fun buildCallRegisterLookupInstructions(tryStart: LabelNode, tryEnd: LabelNode, tryHandler: LabelNode) = InsnList().apply {
    add(MethodInsnNode(
        Opcodes.INVOKESTATIC,
        Type.getInternalName(MethodHandles::class.java),
        "lookup",
        "()${Type.getDescriptor(MethodHandles.Lookup::class.java)}"
    ))
    add(tryStart)
    add(MethodInsnNode(
        Opcodes.INVOKESTATIC,
        Type.getInternalName(registerTransformedFunctionClass),
        registerTransformedFunctionName,
        "(${Type.getDescriptor(MethodHandles.Lookup::class.java)})V"
    ))
    add(tryEnd)
    val end = LabelNode()
    add(JumpInsnNode(Opcodes.GOTO, end))
    add(tryHandler)
    add(InsnNode(Opcodes.POP))
    add(end)
}

private val MethodNode.isStatic: Boolean
    get() = access and Opcodes.ACC_STATIC != 0
