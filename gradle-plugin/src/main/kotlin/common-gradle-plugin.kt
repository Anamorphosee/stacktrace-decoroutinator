@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("CommonGradlePluginKt")

package dev.reformator.stacktracedecoroutinator.gradleplugin

import dev.reformator.stacktracedecoroutinator.generator.internal.addReadProviderModuleToModuleInfo
import dev.reformator.stacktracedecoroutinator.generator.internal.getDebugMetadataInfoFromClassBody
import dev.reformator.stacktracedecoroutinator.generator.internal.transformClassBody
import io.github.oshai.kotlinlogging.KotlinLogging
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

enum class DecoroutinatorTransformedState {
    UNTRANSFORMED,
    TRANSFORMED,
    TRANSFORMED_SKIPPING_SPEC_METHODS
}

val decoroutinatorTransformedStateAttribute: Attribute<DecoroutinatorTransformedState> = Attribute.of(
    "dev.reformator.stacktracedecoroutinator.transformedState",
    DecoroutinatorTransformedState::class.java
)

internal class ObservableProperty<T>(private var _value: T): ReadWriteProperty<Any?, T> {
    private var _set = false

    override fun getValue(thisRef: Any?, property: KProperty<*>): T =
        _value

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        _value = value
        _set = true
    }

    val set: Boolean
        get() = _set

    val value: T
        get() = _value

    fun updateIfNotSet(setter: () -> T) {
        if (!set) {
            _value = setter()
        }
    }
}

class StringMatcherProperty {
    internal val _include = ObservableProperty(setOf<String>())
    internal val _exclude = ObservableProperty(setOf<String>())
    var include: Set<String> by _include
    var exclude: Set<String> by _exclude

    internal val matcher: StringMatcher
        get() = StringMatcher(this)
}

internal class StringMatcher(property: StringMatcherProperty) {
    private val includes = property.include.map { Regex(it) }
    private val excludes = property.exclude.map { Regex(it) }

    fun matches(value: String): Boolean =
        includes.any { it.matches(value) } && excludes.all { !it.matches(value) }
}

open class DecoroutinatorPluginExtension {
    // high level configurations
    var enabled = true
    var addAndroidRuntimeDependency = true
    var addJvmRuntimeDependency = true
    var androidTestsOnly = false
    var jvmTestsOnly = false
    var useTransformedClassesForCompilation = false

    // low level configurations
    internal val _artifactTypes = ObservableProperty(setOf<String>())
    val dependencyConfigurations = StringMatcherProperty()
    val jvmRuntimeDependencyConfigurations = StringMatcherProperty()
    val androidRuntimeDependencyConfigurations = StringMatcherProperty()
    val transformedClassesConfigurations = StringMatcherProperty()
    val transformedClassesSkippingSpecMethodsConfigurations = StringMatcherProperty()
    val tasks = StringMatcherProperty()
    val tasksSkippingSpecMethods = StringMatcherProperty()
    var artifactTypes: Set<String> by _artifactTypes
}

private fun DecoroutinatorPluginExtension.setupLowLevelConfigurations(project: Project) {
    val isAndroid = project.pluginManager.hasPlugin("com.android.base")
    val isKmp = project.pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")

    val androidDepConfigurations = when {
        isKmp && androidTestsOnly -> setOf("androidTestImplementation")
        isKmp -> setOf("androidMainImplementation")
        isAndroid && androidTestsOnly -> setOf("androidTestImplementation")
        isAndroid -> setOf("implementation")
        else -> setOf()
    }
    val jvmDepConfigurations = when {
        isKmp && jvmTestsOnly -> setOf("desktopTestImplementation", "androidUnitTestImplementation")
        isKmp -> setOf("desktopMainImplementation", "androidUnitTestImplementation")
        isAndroid -> setOf("testImplementation")
        else -> setOf("implementation")
    }
    dependencyConfigurations._include.updateIfNotSet {
        androidDepConfigurations + jvmDepConfigurations
    }
    androidRuntimeDependencyConfigurations._include.updateIfNotSet {
        if (addAndroidRuntimeDependency) {
            androidDepConfigurations
        } else {
            emptySet()
        }
    }
    jvmRuntimeDependencyConfigurations._include.updateIfNotSet {
        if (addJvmRuntimeDependency) {
            jvmDepConfigurations
        } else {
            emptySet()
        }
    }

    transformedClassesConfigurations._include.updateIfNotSet {
        when {
            isKmp -> {
                val android = if (androidTestsOnly) {
                    setOf(".*AndroidTestRuntimeClasspath")
                } else {
                    setOf(
                        "androidDebugRuntimeClasspath",
                        "androidReleaseRuntimeClasspath",
                        ".*AndroidTestRuntimeClasspath",
                        "releaseRuntimeClasspath",
                        "debugRuntimeClasspath"
                    )
                }
                val jvm = if (jvmTestsOnly) {
                    setOf(
                        "android.*UnitTestRuntimeClasspath",
                        "desktopTestRuntimeClasspath"
                    )
                } else {
                    setOf(
                        "android.*UnitTestRuntimeClasspath",
                        "desktopRuntimeClasspath"
                    )
                }
                val compile = if (useTransformedClassesForCompilation) {
                    setOf(
                        ".*AndroidTestCompileClasspath",
                        "androidDebugCompileClasspath",
                        "androidReleaseCompileClasspath",
                        "android.*UnitTestCompileClasspath",
                        "desktopTestCompileClasspath",
                        "desktopCompileClasspath"
                    )
                } else {
                    emptySet()
                }
                android + jvm + compile
            }
            isAndroid -> {
                val runtime = if (androidTestsOnly) {
                    setOf(".*AndroidTestRuntimeClasspath")
                } else {
                    setOf(
                        ".*RuntimeClasspath",
                        "runtimeClasspath"
                    )
                }
                val compile = if (useTransformedClassesForCompilation) {
                    setOf(
                        ".*CompileClasspath",
                        "compileClasspath"
                    )
                } else {
                    emptySet()
                }
                runtime + compile
            }
            else -> {
                val runtime = setOf(
                    ".*RuntimeClasspath",
                    "runtimeClasspath"
                )
                val compile = if (useTransformedClassesForCompilation) {
                    setOf(
                        ".*CompileClasspath",
                        "compileClasspath"
                    )
                } else {
                    emptySet()
                }
                runtime + compile
            }
        }
    }

    tasks._include.updateIfNotSet {
        if (!androidTestsOnly && !jvmTestsOnly) {
            return@updateIfNotSet setOf(".*")
        }
        val androidConfigurations = when {
            isKmp && androidTestsOnly -> setOf(".*AndroidTest.*")
            isKmp -> setOf(".*Android.*", ".*android.*")
            isAndroid && androidTestsOnly -> setOf(".*Test.*")
            isAndroid -> setOf(".*")
            else -> emptySet()
        }
        val jvmConfigurations = when {
            isKmp && jvmTestsOnly -> setOf(".*DesktopTest.*")
            isKmp -> setOf(".*Desktop.*")
            !isAndroid && jvmTestsOnly -> setOf(".*Test.*")
            !isAndroid -> setOf(".*")
            else -> emptySet()
        }
        androidConfigurations + jvmConfigurations
    }

    _artifactTypes.updateIfNotSet {
        setOf(
            ArtifactTypeDefinition.JAR_TYPE,
            ArtifactTypeDefinition.JVM_CLASS_DIRECTORY,
            ArtifactTypeDefinition.ZIP_TYPE,
            "aar",
            "android-classes-jar",
            "android-classes-directory"
        )
    }
}

@Suppress("unused")
class DecoroutinatorPlugin: Plugin<Project> {
    override fun apply(target: Project) {
        log.debug { "applying Decoroutinator plugin to ${target.name}" }
        with (target) {
            val pluginExtension = extensions.create(
                ::stacktraceDecoroutinator.name,
                DecoroutinatorPluginExtension::class.java
            )
            dependencies.attributesSchema.attribute(decoroutinatorTransformedStateAttribute)

            afterEvaluate { _ ->
                if (pluginExtension.enabled) {
                    pluginExtension.setupLowLevelConfigurations(target)
                    log.debug { "registering DecoroutinatorArtifactTransformer for types [${pluginExtension.artifactTypes}]" }
                    pluginExtension.artifactTypes.forEach { artifactType ->
                        dependencies.registerTransform(DecoroutinatorTransformAction::class.java) { transformation ->
                            transformation.from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, artifactType)
                            transformation.from.attribute(
                                decoroutinatorTransformedStateAttribute,
                                DecoroutinatorTransformedState.UNTRANSFORMED
                            )
                            transformation.to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, artifactType)
                            transformation.to.attribute(
                                decoroutinatorTransformedStateAttribute,
                                DecoroutinatorTransformedState.TRANSFORMED
                            )
                            transformation.parameters {
                                it.skipSpecMethods.set(false)
                            }
                        }
                        dependencies.registerTransform(DecoroutinatorTransformAction::class.java) { transformation ->
                            transformation.from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, artifactType)
                            transformation.from.attribute(
                                decoroutinatorTransformedStateAttribute,
                                DecoroutinatorTransformedState.UNTRANSFORMED
                            )
                            transformation.to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, artifactType)
                            transformation.to.attribute(
                                decoroutinatorTransformedStateAttribute,
                                DecoroutinatorTransformedState.TRANSFORMED_SKIPPING_SPEC_METHODS
                            )
                            transformation.parameters {
                                it.skipSpecMethods.set(true)
                            }
                        }
                        dependencies.artifactTypes.maybeCreate(artifactType).attributes
                            .attribute(
                                decoroutinatorTransformedStateAttribute,
                                DecoroutinatorTransformedState.UNTRANSFORMED
                            )
                    }

                    //runtime dependency
                    run {
                        val matcher = pluginExtension.dependencyConfigurations.matcher
                        configurations.all { config ->
                            if (matcher.matches(config.name)) {
                                with (dependencies) {
                                    add(config.name, decoroutinatorCommon())
                                    add(config.name, decoroutinatorProvider())
                                    add(config.name, decoroutinatorRegularMethodHandleInvoker())
                                }
                            }
                        }
                    }

                    //android runtime generator dependency
                    run {
                        val matcher = pluginExtension.androidRuntimeDependencyConfigurations.matcher
                        configurations.all { config ->
                            if (matcher.matches(config.name)) {
                                with (dependencies) {
                                    add(config.name, decoroutinatorAndroidRuntime())
                                }
                            }
                        }
                    }

                    //jvm runtime generator dependency
                    run {
                        val matcher = pluginExtension.jvmRuntimeDependencyConfigurations.matcher
                        configurations.all { config ->
                            if (matcher.matches(config.name)) {
                                with (dependencies) {
                                    add(config.name, decoroutinatorJvmRuntime())
                                }
                            }
                        }
                    }

                    run {
                        val matcher = pluginExtension.transformedClassesConfigurations.matcher
                        val skippingSpecMethodsMatcher =
                            pluginExtension.transformedClassesSkippingSpecMethodsConfigurations.matcher
                        configurations.all { config ->
                            val state = when {
                                matcher.matches(config.name) -> DecoroutinatorTransformedState.TRANSFORMED
                                skippingSpecMethodsMatcher.matches(config.name) ->
                                    DecoroutinatorTransformedState.TRANSFORMED_SKIPPING_SPEC_METHODS
                                else -> null
                            }
                            if (state != null) {
                                log.debug { "setting decoroutinatorTransformedStateAttribute for configuration [${config.name}] and state [$state]" }
                                config.attributes.attribute(decoroutinatorTransformedStateAttribute, state)
                            } else {
                                log.debug { "skipping setting decoroutinatorTransformedStateAttribute for configuration [${config.name}]" }
                            }
                        }
                    }

                    run {
                        val matcher = pluginExtension.tasks.matcher
                        val skippingSpecMethodsMatcher = pluginExtension.tasksSkippingSpecMethods.matcher
                        tasks.withType(KotlinJvmCompile::class.java) { task ->
                            val skipSpecMethods = when {
                                matcher.matches(task.name) -> false
                                skippingSpecMethodsMatcher.matches(task.name) -> true
                                else -> null
                            }
                            if (skipSpecMethods != null) {
                                log.debug { "setting transform classes action for task [${task.name}], skipSpecMethods = [$skipSpecMethods]" }
                                task.doLast { _ ->
                                    val dir = task.destinationDirectory.get().asFile
                                    log.debug { "performing in-place transformation of a classes directory [${dir.absolutePath}], skipSpecMethods = [$skipSpecMethods]" }
                                    transformClassesDir(
                                        root = dir,
                                        skipSpecMethods = skipSpecMethods,
                                        onDirectory = { },
                                        onFile = { relativePath, content, modified ->
                                            if (modified) {
                                                val file = dir.resolve(relativePath)
                                                log.debug { "class file [${file.absolutePath}] was transformed, skipSpecMethods = [$skipSpecMethods]" }
                                                file.outputStream().use { output ->
                                                    content.copyTo(output)
                                                }
                                            }
                                        }
                                    )
                                }
                            } else {
                                log.debug { "skipped transform classes action for task [${task.name}]" }
                            }
                        }
                        tasks.withType(AbstractCompile::class.java) { task ->
                            if (matcher.matches(task.name) || skippingSpecMethodsMatcher.matches(task.name)) {
                                log.debug { "setting 'addReadProviderModule' action for task [${task.name}]" }
                                task.doLast { _ ->
                                    visitModuleInfoFiles(task.destinationDirectory.get().asFile) { path, _ ->
                                        val newModuleInfo =
                                            path.inputStream().use { addReadProviderModuleToModuleInfo(it) }
                                        if (newModuleInfo != null) {
                                            path.outputStream().use { it.write(newModuleInfo) }
                                        }
                                    }
                                }
                            } else {
                                log.debug { "skipped 'addReadProviderModule' action for task [${task.name}]" }
                            }
                        }
                    }

                    val setTransformedAttributeAction = Action<Project> { project ->
                        project.configurations.forEach { conf ->
                            conf.outgoing.variants.forEach { variant ->
                                if (variant.artifacts.any { it.type in pluginExtension.artifactTypes }) {
                                    log.debug { "unsetting decoroutinatorTransformedStateAttribute for outgoing variant [${variant.name}] of cofiguarion [${conf.name}]" }
                                    variant.attributes.attribute(
                                        decoroutinatorTransformedStateAttribute,
                                        DecoroutinatorTransformedState.UNTRANSFORMED
                                    )
                                }
                            }
                        }
                    }
                    rootProject.allprojects { project ->
                        if (project.state.executed) {
                            setTransformedAttributeAction.execute(project)
                        } else {
                            project.afterEvaluate(setTransformedAttributeAction)
                        }
                    }
                } else {
                    log.debug { "Decoroutinator plugin is disabled" }
                }
            }
        }
    }
}

abstract class DecoroutinatorTransformAction: TransformAction<DecoroutinatorTransformAction.Parameters> {
    interface Parameters: TransformParameters {
        @get:Input
        val skipSpecMethods: Property<Boolean>
    }

    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val root = inputArtifact.get().asFile
        log.debug { "trying transform artifact [${root.absolutePath}]" }
        if (root.isFile) {
            log.debug { "artifact [${root.absolutePath}] is a file" }
            val needModification = run {
                try {
                    transformZip(
                        zip = root,
                        skipSpecMethods = parameters.skipSpecMethods.get(),
                        putNextEntry = { },
                        putFileBody = { modified, _ ->
                            if (modified) {
                                return@run true
                            }
                        },
                        closeEntry = { }
                    )
                } catch (_: IOException) { }
                false
            }
            if (needModification) {
                val suffix = root.name.lastIndexOf('.').let { index ->
                    if (index == -1) "" else root.name.substring(index)
                }
                val newName = root.name.removeSuffix(suffix) + "-decoroutinator" + suffix
                val newFile = outputs.file(newName)
                ZipOutputStream(newFile.outputStream()).use { output ->
                    transformZip(
                        zip = root,
                        skipSpecMethods = parameters.skipSpecMethods.get(),
                        putNextEntry = { output.putNextEntry(it) },
                        putFileBody = { _, body -> body.copyTo(output) },
                        closeEntry = { output.closeEntry() }
                    )
                }
                log.debug { "file artifact [${root.absolutePath}] was transformed to [${newFile.absolutePath}]" }
            } else {
                log.debug { "file artifact [${root.absolutePath}] was skipped" }
                outputs.file(inputArtifact)
            }
        } else if (root.isDirectory) {
            log.debug { "artifact [${root.absolutePath}] is a directory" }
            val needModification = run {
                transformClassesDir(
                    root = root,
                    skipSpecMethods = parameters.skipSpecMethods.get(),
                    onDirectory = { },
                    onFile = { _, _, modified ->
                        if (modified) return@run true
                    }
                )
                false
            }
            if (needModification) {
                val newRoot = outputs.dir(root.name + "-decoroutinator")
                transformClassesDir(
                    root = root,
                    skipSpecMethods = parameters.skipSpecMethods.get(),
                    onDirectory = { newRoot.resolve(it).mkdir() },
                    onFile = { relativePath, content, _ ->
                        newRoot.resolve(relativePath).outputStream().use { output ->
                            content.copyTo(output)
                        }
                    }
                )
                log.debug { "directory artifact [${root.absolutePath}] was transformed to [${newRoot.absolutePath}]" }
            } else {
                log.debug { "directory artifact [${root.absolutePath}] was skipped" }
                outputs.dir(inputArtifact)
            }
        } else {
            error("artifact [${root.absolutePath}] does not exist")
        }
    }
}

private const val CLASS_EXTENSION = ".class"
private const val MODULE_INFO_CLASS_NAME = "module-info.class"
private val log = KotlinLogging.logger { }

private inline fun transformZip(
    zip: File,
    skipSpecMethods: Boolean,
    putNextEntry: (ZipEntry) -> Unit,
    putFileBody: (modified: Boolean, body: InputStream) -> Unit,
    closeEntry: () -> Unit
) {
    val names = mutableSetOf<String>()
    ZipFile(zip).use { input ->
        var readProviderModule = false

        input.entries().asSequence().forEach { entry: ZipEntry ->
            if (entry.isDirectory || !entry.name.isModuleInfo) {
                if (!names.add(entry.name)) {
                    log.warn { "Duplicate zip entry '${entry.name}' in file '$zip'. Ignoring it" }
                    return@forEach
                }
                putNextEntry(ZipEntry(entry.name).apply {
                    entry.lastModifiedTime?.let { lastModifiedTime = it }
                    entry.lastAccessTime?.let { lastAccessTime = it }
                    entry.creationTime?.let { creationTime = it }
                    method = ZipEntry.DEFLATED
                    comment = entry.comment
                })
                if (!entry.isDirectory) {
                    var newBody: ByteArray? = null
                    if (entry.name.isClass) {
                        val transformationStatus = input.getInputStream(entry).use { classBody ->
                            transformClassBody(
                                classBody = classBody,
                                metadataResolver = metadataResolver@{ metadataClassName ->
                                    val entryName = metadataClassName.replace('.', '/') + CLASS_EXTENSION
                                    val classEntry = input.getEntry(entryName) ?: return@metadataResolver null
                                    input.getInputStream(classEntry).use {
                                        getDebugMetadataInfoFromClassBody(it)
                                    }
                                },
                                skipSpecMethods = skipSpecMethods
                            )
                        }
                        readProviderModule = readProviderModule || transformationStatus.needReadProviderModule
                        newBody = transformationStatus.updatedBody
                    }
                    val modified = newBody != null
                    (if (modified) ByteArrayInputStream(newBody) else input.getInputStream(entry)).use { body ->
                        putFileBody(modified, body)
                    }
                }
                closeEntry()
            }
        }

        input.entries().asSequence().forEach { entry: ZipEntry ->
            if (!entry.isDirectory && entry.name.isModuleInfo) {
                if (!names.add(entry.name)) {
                    log.warn { "Duplicate module-info entry '${entry.name}' in file '$zip'. Ignoring it" }
                    return@forEach
                }
                putNextEntry(ZipEntry(entry.name).apply {
                    entry.lastModifiedTime?.let { lastModifiedTime = it }
                    entry.lastAccessTime?.let { lastAccessTime = it }
                    entry.creationTime?.let { creationTime = it }
                    method = ZipEntry.DEFLATED
                    comment = entry.comment
                })
                var newBody: ByteArray? = null
                if (readProviderModule) {
                    newBody = input.getInputStream(entry).use { moduleInfoBody ->
                        addReadProviderModuleToModuleInfo(moduleInfoBody)
                    }
                }
                val modified = newBody != null
                (if (modified) newBody!!.inputStream() else input.getInputStream(entry)).use { body ->
                    putFileBody(modified, body)
                }
                closeEntry()
            }
        }
    }
}

private inline fun transformClassesDir(
    root: File,
    skipSpecMethods: Boolean,
    onDirectory: (relativePath: File) -> Unit,
    onFile: (relativePath: File, content: InputStream, modified: Boolean) -> Unit
) {
    var readProviderModule = false

    root.walk().forEach { file ->
        val relativePath = file.relativeTo(root)
        if (file.isFile && file.isClass) {
            val transformationStatus = file.inputStream().use { classBody ->
                transformClassBody(
                    classBody = classBody,
                    metadataResolver = { metadataClassName ->
                        val metadataClassRelativePath = metadataClassName.replace('.', File.separatorChar) + CLASS_EXTENSION
                        val classPath = root.resolve(metadataClassRelativePath)
                        if (classPath.isFile) {
                            classPath.inputStream().use {
                                getDebugMetadataInfoFromClassBody(it)
                            }
                        } else {
                            null
                        }
                    },
                    skipSpecMethods = skipSpecMethods
                )
            }
            readProviderModule = readProviderModule || transformationStatus.needReadProviderModule
            transformationStatus.updatedBody?.let {
                onFile(relativePath, it.inputStream(), true)
                return@forEach
            }
        }
        if (file.isDirectory) {
            onDirectory(relativePath)
            return@forEach
        }
        if (!file.isModuleInfo) {
            file.inputStream().use { input ->
                onFile(relativePath, input, false)
            }
        }
    }

    visitModuleInfoFiles(root) { path, relativePath ->
        var newBody: ByteArray? = null
        if (readProviderModule) {
            newBody = path.inputStream().use { addReadProviderModuleToModuleInfo(it) }
        }
        val modified = newBody != null
        onFile(relativePath, if (modified) newBody!!.inputStream() else path.inputStream(), modified)
    }
}

private inline fun visitModuleInfoFiles(root: File, onModuleInfoFile: (path: File, relativePath: File) -> Unit) {
    root.walk().forEach { file ->
        if (file.isFile && file.isModuleInfo) {
            val relativePath = file.relativeTo(root)
            onModuleInfoFile(file, relativePath)
        }
    }
}

private val String.isModuleInfo: Boolean
    get() = substringAfterLast('/') == MODULE_INFO_CLASS_NAME

private val String.isClass: Boolean
    get() = endsWith(CLASS_EXTENSION) && !isModuleInfo

private val File.isModuleInfo: Boolean
    get() = name == MODULE_INFO_CLASS_NAME

private val File.isClass: Boolean
    get() = name.endsWith(CLASS_EXTENSION) && !isModuleInfo
