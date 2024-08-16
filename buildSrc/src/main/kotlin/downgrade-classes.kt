@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.buildsrc

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.io.InputStream

class DowngradeClassesPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            tasks.withType(JavaCompile::class.java) { task ->
                task.doLast { _ ->
                    task.destinationDirectory.asFileTree.visit { path ->
                        if (!path.isDirectory && path.name.endsWith(".class") && path.name != "module-info.class") {
                            val node = path.file.toPath().readClassNode() ?: return@visit
                            if (node.version > Opcodes.V1_8) {
                                node.version = Opcodes.V1_8
                                node.writeTo(path.file)
                            }
                        }
                    }
                }
            }
        }
    }
}
