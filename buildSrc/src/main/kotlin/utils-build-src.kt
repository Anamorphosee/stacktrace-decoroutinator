@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.buildsrc

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.inputStream

fun ClassNode.writeTo(file: File) {
    val writer = ClassWriter(0)
    accept(writer)
    file.outputStream().use { it.write(writer.toByteArray()) }
}

fun Path.readClassNode(): ClassNode? =
    inputStream().use { input ->
        try {
            val classReader = ClassReader(input)
            val classNode = ClassNode(Opcodes.ASM9)
            classReader.accept(classNode, 0)
            classNode
        } catch (_: Exception) {
            null
        }
    }
