@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.buildsrc

import dev.reformator.stacktracedecoroutinator.intrinsics.ReplaceClassWith
import dev.reformator.stacktracedecoroutinator.intrinsics.Skip
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.tasks.JvmConstants
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jetbrains.kotlin.konan.file.file
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import java.io.File
import java.nio.file.FileSystems
import kotlin.io.path.*

class ApplyIntrinsicsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with (target) {
            dependencies.add(JvmConstants.COMPILE_ONLY_CONFIGURATION_NAME, project(":intrinsics"))
            tasks.withType(KotlinJvmCompile::class.java) { task ->
                task.doLast { _ ->
                    task.destinationDirectory.asFileTree.visit { path ->
                        if (!path.isDirectory && path.name.endsWith(".class")) {
                            val node = path.file.toPath().readClassNode() ?: return@visit
                            var modified = false
                            node.superName?.replaceClasses {
                                modified = true
                                node.superName = it
                            }
                            node.interfaces = node.interfaces?.map {
                                it.replaceClasses { modified = true }
                            }
                            node.signature?.replaceClasses {
                                modified = true
                                node.signature = it
                            }

                            node.fields.orEmpty().forEach { field ->
                                field.desc.replaceClasses {
                                    modified = true
                                    field.desc = it
                                }
                                field.signature?.replaceClasses {
                                    modified = true
                                    field.signature = it
                                }
                            }

                            node.methods.orEmpty().forEach { method ->
                                method.desc.replaceClasses {
                                    modified = true
                                    method.desc = it
                                }
                                method.signature?.replaceClasses {
                                    modified = true
                                    method.signature = it
                                }
                                method.localVariables.orEmpty().forEach { variable ->
                                    variable.desc.replaceClasses {
                                        modified = true
                                        variable.desc = it
                                    }
                                    variable.signature?.replaceClasses {
                                        modified = true
                                        variable.signature = it
                                    }
                                }
                                method.instructions?.forEach { instruction ->
                                    when (instruction) {
                                        is MethodInsnNode -> {
                                            val key = MethodKey(
                                                className = instruction.owner,
                                                name = instruction.name,
                                                descriptor = instruction.desc
                                            )
                                            if (key in intrinsics.skippedMethods) {
                                                modified = true
                                                method.instructions.remove(instruction)
                                            } else {
                                                intrinsics.replacedClassesByMethod[key]?.let {
                                                    modified = true
                                                    instruction.owner = it
                                                }
                                                instruction.owner.replaceClasses {
                                                    modified = true
                                                    instruction.owner = it
                                                }
                                                instruction.desc?.replaceClasses {
                                                    modified = true
                                                    instruction.desc = it
                                                }
                                            }
                                        }
                                        is FieldInsnNode -> {
                                            instruction.owner.replaceClasses {
                                                modified = true
                                                instruction.owner = it
                                            }
                                            instruction.desc.replaceClasses {
                                                modified = true
                                                instruction.desc = it
                                            }
                                        }
                                        is FrameNode -> {
                                            instruction.local = instruction.local.replaceClassesInFrame { modified = true }
                                            instruction.stack = instruction.stack.replaceClassesInFrame { modified = true }
                                        }
                                        is LdcInsnNode -> {
                                            val cst = instruction.cst
                                            if (cst is Type) {
                                                cst.descriptor.replaceClasses {
                                                    modified = true
                                                    instruction.cst = Type.getType(it)
                                                }
                                            }
                                        }
                                        is MultiANewArrayInsnNode -> {
                                            instruction.desc.replaceClasses {
                                                modified = true
                                                instruction.desc = it
                                            }
                                        }
                                        is TypeInsnNode -> {
                                            instruction.desc.replaceClasses {
                                                modified = true
                                                instruction.desc = it
                                            }
                                        }
                                    }
                                }
                            }
                            if (modified) {
                                node.writeTo(path.file)
                            }
                        }
                    }
                }
            }
            tasks.withType(JavaCompile::class.java) { task ->
                task.doLast { _ ->
                    task.destinationDirectory.asFileTree.visit { path ->
                        if (!path.isDirectory && path.name == "module-info.class") {
                            val node = path.file.toPath().readClassNode() ?: return@visit
                            if (node.module != null && node.module.requires.orEmpty().any { it.module == INTRINSICS_MODULE }) {
                                node.module.requires.removeIf { it.module == INTRINSICS_MODULE }
                                node.writeTo(path.file)
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val INTRINSICS_MODULE = "intrinsics"

private data class MethodKey(
    val className: String,
    val name: String,
    val descriptor: String
)

private class Intrinsics(
    val replacedClasses: Map<String, String>,
    val replacedClassesByMethod: Map<MethodKey, String>,
    val skippedMethods: Set<MethodKey>
)

@OptIn(ExperimentalPathApi::class)
private val intrinsics = run {
    val classLoader: ClassLoader = Thread.currentThread().contextClassLoader
    val path = ReplaceClassWith::class.java.packageName.replace('.', '/')

    val replacedClasses = mutableMapOf<String, String>()
    val replacedClassesByMethod = mutableMapOf<MethodKey, String>()
    val skippedMethods = mutableSetOf<MethodKey>()

    classLoader.getResources(path).asSequence().forEach { rootPath ->
        FileSystems.newFileSystem(rootPath.toURI(), emptyMap<String, Any?>()).use { fs ->
            fs.getPath("/").walk().forEach { path ->
                if (path.isRegularFile() && path.name.endsWith(".class")) {
                    val clazz = path.readClassNode()
                    if (clazz != null) {
                        clazz.invisibleAnnotations.replaceClassWith?.let {
                            replacedClasses[clazz.name] = it
                        }
                        clazz.methods.orEmpty().forEach { method ->
                            if (method.invisibleAnnotations.skip) {
                                skippedMethods.add(method.getMethodKey(clazz.name))
                            } else {
                                method.invisibleAnnotations.replaceClassWith?.let {
                                    replacedClassesByMethod[method.getMethodKey(clazz.name)] = it
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Intrinsics(
        replacedClasses = replacedClasses,
        replacedClassesByMethod = replacedClassesByMethod,
        skippedMethods = skippedMethods
    )
}

private val List<AnnotationNode>?.skip: Boolean
    get() = orEmpty().any { it.desc == Type.getDescriptor(Skip::class.java) }

private val List<AnnotationNode>?.replaceClassWith: String?
    get() = orEmpty().find { it.desc == Type.getDescriptor(ReplaceClassWith::class.java) }?.values?.get(1) as String?

private fun MethodNode.getMethodKey(className: String) =
    MethodKey(
        className = className,
        name = name,
        descriptor = desc
    )

private fun String.replaceClasses(ifChanged: (newString: String) -> Unit): String {
    var result = this
    intrinsics.replacedClasses.forEach { (from, to) ->
        result = result.replace(from, to)
    }
    if (result != this) {
        ifChanged(result)
    }
    return result
}

private fun List<Any>?.replaceClassesInFrame(ifChanged: () -> Unit): List<Any>? {
    var modified = false
    val result = this?.map {
        if (it is String) {
            it.replaceClasses { modified = true }
        } else {
            it
        }
    }
    if (modified) {
        ifChanged()
    }
    return result
}

