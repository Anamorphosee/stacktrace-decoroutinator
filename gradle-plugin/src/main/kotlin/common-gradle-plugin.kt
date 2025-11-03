@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("CommonGradlePluginKt")

package dev.reformator.stacktracedecoroutinator.gradleplugin

import dev.reformator.stacktracedecoroutinator.intrinsics.PROVIDER_MODULE_NAME
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorTransformed
import dev.reformator.stacktracedecoroutinator.provider.internal.AndroidKeep
import dev.reformator.stacktracedecoroutinator.provider.internal.AndroidLegacyKeep
import io.github.oshai.kotlinlogging.KotlinLogging
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.io.File
import java.util.ServiceLoader
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

private val log = KotlinLogging.logger { }

internal object DecoroutinatorTransformedState {
    const val UNTRANSFORMED = "UNTRANSFORMED"
    const val TRANSFORMED = "TRANSFORMED"
    const val TRANSFORMED_SKIPPING_SPEC_METHODS = "TRANSFORMED_SKIPPING_SPEC_METHODS"
}

internal val decoroutinatorTransformedStateAttribute: Attribute<String> = Attribute.of(
    "dev.reformator.stacktracedecoroutinator.transformedState3",
    String::class.java
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

    fun updateIfNotSet(setter: (T) -> T) {
        if (!set) {
            _value = setter(_value)
        }
    }
}

@Suppress("PropertyName")
class StringMatcherProperty {
    internal val _include = ObservableProperty(setOf<String>())
    internal val _exclude = ObservableProperty(setOf<String>())
    var include: Set<String> by _include
    var exclude: Set<String> by _exclude

    internal val matcher: StringMatcher
        get() = StringMatcher(this)
}

internal class StringMatcher(property: StringMatcherProperty) {
    private val includes = property.include.asSequence().distinct().map { Regex(it) }.toList()
    private val excludes = property.exclude.asSequence().distinct().map { Regex(it) }.toList()

    fun matches(value: String): Boolean =
        includes.any { it.matches(value) } && excludes.all { !it.matches(value) }
}

internal val defaultArtifactTypes = setOf(
    ArtifactTypeDefinition.JAR_TYPE,
    ArtifactTypeDefinition.JVM_CLASS_DIRECTORY,
    ArtifactTypeDefinition.ZIP_TYPE,
    "aar",
    "android-classes-directory"
)

@Suppress("PropertyName")
open class DecoroutinatorPluginExtension {
    // high level configurations
    internal val _legacyAndroidCompatibility = ObservableProperty(false)
    internal val _embedDebugProbesForAndroidTest = ObservableProperty(false)
    var enabled = true
    var addAndroidRuntimeDependency = true
    var addJvmRuntimeDependency = true
    var androidTestsOnly = false
    var jvmTestsOnly = false
    var useTransformedClassesForCompilation = false
    var legacyAndroidCompatibility: Boolean by _legacyAndroidCompatibility
    var embedDebugProbesForAndroid = false
    var embedDebugProbesForAndroidTest: Boolean by _embedDebugProbesForAndroidTest

    // low level configurations
    val regularDependencyConfigurations = StringMatcherProperty()
    val androidDependencyConfigurations = StringMatcherProperty()
    val jvmDependencyConfigurations = StringMatcherProperty()
    val jvmRuntimeDependencyConfigurations = StringMatcherProperty()
    val androidRuntimeDependencyConfigurations = StringMatcherProperty()
    val transformedClassesConfigurations = StringMatcherProperty()
    val transformedClassesSkippingSpecMethodsConfigurations = StringMatcherProperty()
    val tasks = StringMatcherProperty()
    val tasksSkippingSpecMethods = StringMatcherProperty()
    var artifactTypes = defaultArtifactTypes
    val embeddedDebugProbesConfigurations = StringMatcherProperty()
    val runtimeSettingsDependencyConfigurations = StringMatcherProperty()
}

private val Project.isAndroid: Boolean
    get() = pluginManager.hasPlugin("com.android.base")

private val Project.isKmp: Boolean
    get() = pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")

private val Project.androidMinSdk: Int?
    get() {
        try {
            val ext = extensions.getByName("android") ?: return null
            val defaultConfig = ext.javaClass.getMethod("getDefaultConfig").invoke(ext) ?: return null
            return defaultConfig.javaClass.getMethod("getMinSdk").invoke(defaultConfig) as? Int?
        } catch (_: UnknownDomainObjectException) {
            return null
        } catch (_: ReflectiveOperationException) {
            return null
        }
    }

private fun DecoroutinatorPluginExtension.setupHighLevelConfig(project: Project) {
    _legacyAndroidCompatibility.updateIfNotSet {
        if (!project.isAndroid) {
            return@updateIfNotSet false
        }
        val minSdk = project.androidMinSdk
        minSdk != null && minSdk < 26
    }
    _embedDebugProbesForAndroidTest.updateIfNotSet {
        embedDebugProbesForAndroid
    }
}

private fun DecoroutinatorPluginExtension.setupLowLevelDependencyConfig(project: Project) {
    val isAndroid = project.isAndroid
    val isKmp = project.isKmp
    val androidTestConfigs = when {
        isKmp -> setOf("androidTestImplementation", "androidInstrumentedTestImplementation")
        isAndroid -> setOf("androidTestImplementation", "testImplementation")
        else -> emptySet()
    }
    val androidMainConfigs = when {
        isKmp -> setOf("androidMainImplementation")
        isAndroid -> setOf("implementation")
        else -> emptySet()
    }
    val jvmTestConfigs = when {
        isKmp -> setOf("desktopTestImplementation", "androidUnitTestImplementation")
        isAndroid -> setOf(".*UnitTestImplementation", "unitTestImplementation")
        else -> setOf("testImplementation")
    }
    val jvmMainConfigs = when {
        isKmp -> setOf("desktopMainImplementation", "androidUnitTestImplementation")
        isAndroid -> setOf(".*UnitTestImplementation", "unitTestImplementation")
        else -> setOf("implementation")
    }
    val androidConfigs = if (androidTestsOnly) androidTestConfigs else androidMainConfigs
    val jvmConfigs = if (jvmTestsOnly) jvmTestConfigs else jvmMainConfigs
    regularDependencyConfigurations._include.updateIfNotSet {
        if (legacyAndroidCompatibility) emptySet() else androidConfigs + jvmConfigs
    }
    androidDependencyConfigurations._include.updateIfNotSet {
        androidConfigs
    }
    jvmDependencyConfigurations._include.updateIfNotSet {
        jvmConfigs
    }
    androidRuntimeDependencyConfigurations._include.updateIfNotSet {
        if (addAndroidRuntimeDependency) {
            androidConfigs
        } else {
            emptySet()
        }
    }
    jvmRuntimeDependencyConfigurations._include.updateIfNotSet {
        if (addJvmRuntimeDependency) {
            jvmConfigs
        } else {
            emptySet()
        }
    }
    runtimeSettingsDependencyConfigurations._include.updateIfNotSet {
        when {
            embedDebugProbesForAndroid -> androidMainConfigs
            embedDebugProbesForAndroidTest -> androidTestConfigs
            else -> emptySet()
        }
    }
}

private fun DecoroutinatorPluginExtension.setupLowLevelRuntimeTransformedClassesConfig(project: Project) {
    val isAndroid = project.isAndroid
    val isKmp = project.isKmp
    val androidConfigurations = when {
        isKmp -> {
            if (androidTestsOnly) {
                setOf(".*AndroidTestRuntimeClasspath", "androidTestRuntimeClasspath")
            } else {
                setOf(
                    "androidDebugRuntimeClasspath",
                    "androidReleaseRuntimeClasspath",
                    ".*AndroidTestRuntimeClasspath",
                    "androidTestRuntimeClasspath",
                    "debugRuntimeClasspath",
                    "releaseRuntimeClasspath"
                )
            }
        }
        isAndroid -> {
            if (androidTestsOnly) {
                setOf(".*AndroidTestRuntimeClasspath", "androidTestRuntimeClasspath")
            } else {
                setOf(
                    ".*RuntimeClasspath",
                    "runtimeClasspath",
                    "debugRuntimeClasspath",
                    "releaseRuntimeClasspath"
                )
            }
        }
        else -> emptySet()
    }
    val jvmConfigurations = when {
        isKmp -> {
            if (jvmTestsOnly) {
                setOf(".*UnitTestRuntimeClasspath", "desktopTestRuntimeClasspath")
            } else {
                setOf(".*UnitTestRuntimeClasspath", "desktopRuntimeClasspath", "desktopMainRuntimeClasspath")
            }
        }
        isAndroid -> setOf(".*UnitTestRuntimeClasspath", "unitTestRuntimeClasspath")
        else -> {
            if (jvmTestsOnly) {
                setOf(".*TestRuntimeClasspath", "testRuntimeClasspath")
            } else {
                setOf(".*RuntimeClasspath", "runtimeClasspath")
            }
        }
    }
    transformedClassesConfigurations._include.updateIfNotSet {
        if (legacyAndroidCompatibility) {
            emptySet()
        } else {
            androidConfigurations + jvmConfigurations
        }
    }
    transformedClassesSkippingSpecMethodsConfigurations._include.updateIfNotSet {
        if (legacyAndroidCompatibility) {
            androidConfigurations + jvmConfigurations
        } else {
            emptySet()
        }
    }
}

private fun DecoroutinatorPluginExtension.setupLowLevelEmbeddedDebugProbesConfigurations(project: Project) {
    val isKmp = project.isKmp
    val testConfigs = when {
        isKmp -> setOf(".*AndroidTestRuntimeClasspath", "androidTestRuntimeClasspath")
        else -> setOf(".*AndroidTestRuntimeClasspath", "androidTestRuntimeClasspath")
    }
    val nonTestConfigs = when {
        isKmp -> {
            setOf(
                "androidDebugRuntimeClasspath",
                "androidReleaseRuntimeClasspath",
                ".*AndroidTestRuntimeClasspath",
                "androidTestRuntimeClasspath",
            )
        }
        else -> setOf(".*RuntimeClasspath", "runtimeClasspath")
    }
    embeddedDebugProbesConfigurations._include.updateIfNotSet {
        when {
            embedDebugProbesForAndroid -> nonTestConfigs
            embedDebugProbesForAndroidTest -> testConfigs
            else -> emptySet()
        }
    }
}

private fun DecoroutinatorPluginExtension.setupLowLevelCompileTransformedClassesConfig(project: Project) {
    val isAndroid = project.isAndroid
    val isKmp = project.isKmp
    val androidConfigurations = when {
        isKmp -> {
            if (androidTestsOnly) {
                setOf(".*AndroidTestCompileClasspath", "androidTestCompileClasspath")
            } else {
                setOf(
                    "androidDebugCompileClasspath",
                    "androidReleaseCompileClasspath",
                    ".*AndroidTestCompileClasspath",
                    "androidTestCompileClasspath",
                )
            }
        }
        isAndroid -> {
            if (androidTestsOnly) {
                setOf(".*AndroidTestCompileClasspath", "androidTestCompileClasspath")
            } else {
                setOf(".*CompileClasspath", "compileClasspath")
            }
        }
        else -> emptySet()
    }
    val jvmConfigurations = when {
        isKmp -> {
            if (jvmTestsOnly) {
                setOf("android.*UnitTestCompileClasspath", "desktopTestCompileClasspath")
            } else {
                setOf("android.*UnitTestCompileClasspath", "desktopCompileClasspath")
            }
        }
        isAndroid -> setOf(".*UnitTestCompileClasspath", "unitTestCompileClasspath")
        else -> {
            if (jvmTestsOnly) {
                setOf(".*TestCompileClasspath", "testCompileClasspath")
            } else {
                setOf(".*CompileClasspath", "compileClasspath")
            }
        }
    }
    transformedClassesConfigurations._include.updateIfNotSet {
        if (legacyAndroidCompatibility) {
            it
        } else {
            it + androidConfigurations + jvmConfigurations
        }
    }
    transformedClassesSkippingSpecMethodsConfigurations._include.updateIfNotSet {
        if (legacyAndroidCompatibility) {
            it + androidConfigurations + jvmConfigurations
        } else {
            it
        }
    }
}

private fun DecoroutinatorPluginExtension.setupLowLevelTasksConfig(project: Project) {
    val taskPatterns = if (!androidTestsOnly && !jvmTestsOnly) {
        setOf(".*")
    } else {
        val isAndroid = project.isAndroid
        val isKmp = project.isKmp
        val androidTasks = when {
            isKmp && androidTestsOnly -> setOf(".*AndroidTest.*")
            isKmp -> setOf(".*Android.*", ".*android.*")
            isAndroid && androidTestsOnly -> setOf(".*Test.*")
            isAndroid -> setOf(".*")
            else -> emptySet()
        }
        val jvmTasks = when {
            isKmp && jvmTestsOnly -> setOf(".*DesktopTest.*")
            isKmp -> setOf(".*Desktop.*")
            !isAndroid && jvmTestsOnly -> setOf(".*Test.*")
            !isAndroid -> setOf(".*")
            else -> emptySet()
        }
        androidTasks + jvmTasks
    }
    tasks._include.updateIfNotSet {
        if (legacyAndroidCompatibility) emptySet() else taskPatterns
    }
    tasksSkippingSpecMethods._include.updateIfNotSet {
        if (legacyAndroidCompatibility) taskPatterns else emptySet()
    }
}

private fun DecoroutinatorPluginExtension.setupLowLevelConfig(project: Project) {
    setupHighLevelConfig(project)
    setupLowLevelDependencyConfig(project)
    setupLowLevelRuntimeTransformedClassesConfig(project)
    if (useTransformedClassesForCompilation) {
        setupLowLevelCompileTransformedClassesConfig(project)
    }
    setupLowLevelTasksConfig(project)
    setupLowLevelEmbeddedDebugProbesConfigurations(project)
}

internal fun createUnsetDecoroutinatorTransformedStateAttributeAction(artifactTypes: Set<String>): Action<Project> =
    Action<Project> { project ->
        project.configurations.configureEach { conf ->
            conf.outgoing.variants.configureEach { variant ->
                if (variant.artifacts.any { it.type in artifactTypes }) {
                    val attr = variant.attributes.getAttribute(decoroutinatorTransformedStateAttribute)
                    if (attr != null) {
                        log.debug {
                            "decoroutinatorTransformedStateAttribute for outgoing variant [$variant] of" +
                                    "configuration [${conf.name}] in project [${project.name}] is already set to [$attr]"
                        }
                    } else {
                        log.debug {
                            "unsetting decoroutinatorTransformedStateAttribute for outgoing variant" +
                                    "[$variant] of configuration [${conf.name}] in project [${project.name}]"
                        }
                        try {
                            variant.attributes.attribute(
                                decoroutinatorTransformedStateAttribute,
                                DecoroutinatorTransformedState.UNTRANSFORMED
                            )
                        } catch (e: IllegalStateException) {
                            val message =
                                "Failed to set the necessary attribute for the project " +
                                        "[${project.name}]. Please apply the " +
                                        "'dev.reformator.stacktracedecoroutinator.attribute' plugin to it."
                            throw IllegalStateException(message, e)
                        }
                    }
                }
            }
        }
    }

@Suppress("unused")
class DecoroutinatorPlugin: Plugin<Project> {
    override fun apply(target: Project) {
        log.debug { "applying Decoroutinator plugin to [${target.name}]" }
        groovyDslInitializer.initGroovyDsl(target)
        with (target) {
            val pluginExtension = extensions.create(
                ::stacktraceDecoroutinator.name,
                DecoroutinatorPluginExtension::class.java
            )
            dependencies.attributesSchema.attribute(decoroutinatorTransformedStateAttribute)
            dependencies.attributesSchema.attribute(decoroutinatorEmbeddedDebugProbesAttribute)

            afterEvaluate { _ ->
                if (pluginExtension.enabled) {
                    pluginExtension.setupLowLevelConfig(target)
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
                        dependencies.registerTransform(DecoroutinatorEmbedDebugProbesAction::class.java) { transformation ->
                            transformation.from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, artifactType)
                            transformation.from.attribute(decoroutinatorEmbeddedDebugProbesAttribute, false)
                            transformation.to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, artifactType)
                            transformation.to.attribute(decoroutinatorEmbeddedDebugProbesAttribute, true)
                        }
                        dependencies.artifactTypes.maybeCreate(artifactType).attributes
                            .attribute(
                                decoroutinatorTransformedStateAttribute,
                                DecoroutinatorTransformedState.UNTRANSFORMED
                            )
                            .attribute(
                                decoroutinatorEmbeddedDebugProbesAttribute,
                                false
                            )
                    }

                    //dependency configurations
                    run {
                        val regularMatcher = pluginExtension.regularDependencyConfigurations.matcher
                        val androidMatcher = pluginExtension.androidDependencyConfigurations.matcher
                        val jvmMatcher = pluginExtension.jvmDependencyConfigurations.matcher
                        configurations.configureEach { config ->
                            with (dependencies) {
                                if (regularMatcher.matches(config.name)) {
                                    add(config.name, decoroutinatorCommon())
                                    add(config.name, decoroutinatorRegularMethodHandleInvoker())
                                } else {
                                    if (androidMatcher.matches(config.name)) {
                                        add(config.name, decoroutinatorCommon())
                                        add(config.name, decoroutinatorAndroidMethodHandleInvoker())
                                    }
                                    if (jvmMatcher.matches(config.name)) {
                                        add(config.name, decoroutinatorCommon())
                                        add(config.name, decoroutinatorJvmMethodHandleInvoker())
                                    }
                                }
                            }
                        }
                    }

                    //android runtime generator dependency
                    run {
                        val matcher = pluginExtension.androidRuntimeDependencyConfigurations.matcher
                        configurations.configureEach { config ->
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
                        configurations.configureEach { config ->
                            if (matcher.matches(config.name)) {
                                with (dependencies) {
                                    add(config.name, decoroutinatorJvmRuntime())
                                }
                            }
                        }
                    }

                    //runtime settings dependency
                    run {
                        val matcher = pluginExtension.runtimeSettingsDependencyConfigurations.matcher
                        configurations.configureEach { config ->
                            if (matcher.matches(config.name)) {
                                with (dependencies) {
                                    add(config.name, decoroutinatorRuntimeSettings())
                                }
                            }
                        }
                    }

                    run {
                        val matcher = pluginExtension.transformedClassesConfigurations.matcher
                        val skippingSpecMethodsMatcher =
                            pluginExtension.transformedClassesSkippingSpecMethodsConfigurations.matcher
                        configurations.configureEach { config ->
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
                        val matcher = pluginExtension.embeddedDebugProbesConfigurations.matcher
                        configurations.configureEach { config ->
                            if (matcher.matches(config.name)) {
                                log.debug { "setting decoroutinatorEmbeddedDebugProbesAttribute for configuration [${config.name}]" }
                                config.attributes.attribute(decoroutinatorEmbeddedDebugProbesAttribute, true)
                            } else {
                                log.debug { "skipping setting decoroutinatorEmbeddedDebugProbesAttribute for configuration [${config.name}]" }
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
                                    val artifact = DirectoryArtifact(dir)
                                    artifact.transform(
                                        skipSpecMethods = skipSpecMethods,
                                        onFile = { modified, path, body ->
                                            if (modified) {
                                                log.debug { "class file [$path] was transformed, skipSpecMethods = [$skipSpecMethods]" }
                                                artifact.addFile(path, body)
                                            }
                                            true
                                        },
                                        onDirectory = { _ -> }
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
                                        val moduleNode = path.inputStream().use { tryReadModuleInfo(it) }
                                        if (moduleNode != null) {
                                            log.debug { "adding read provider module for file [${path.absolutePath}]" }
                                            moduleNode.module.addRequiresModule(PROVIDER_MODULE_NAME)
                                            path.outputStream().use { it.write(moduleNode.classBody) }
                                        }
                                    }
                                }
                            } else {
                                log.debug { "skipped 'addReadProviderModule' action for task [${task.name}]" }
                            }
                        }
                    }

                    val unsetTransformedAttributeAction = createUnsetDecoroutinatorTransformedStateAttributeAction(
                        artifactTypes = pluginExtension.artifactTypes
                    )
                    rootProject.allprojects { project ->
                        if (project.state.executed) {
                            unsetTransformedAttributeAction.execute(project)
                        } else {
                            project.afterEvaluate(unsetTransformedAttributeAction)
                        }
                    }

                    if (isAndroid) {
                        val extractProguardFilesTaskName = "extractProguardFiles"
                        val extractProguardFilesTask = tasks.findByName(extractProguardFilesTaskName)
                        if (extractProguardFilesTask == null) {
                            log.error { "Task [extractProguardFiles] was not found" }
                        } else {
                            val dir = decoroutinatorDir
                            val legacyAndroidCompatibility = pluginExtension.legacyAndroidCompatibility
                            extractProguardFilesTask.doLast { _ ->
                                dir.mkdirs()
                                dir.resolve(ANDROID_PROGUARD_RULES_FILE_NAME).writeText(ANDROID_PROGUARD_RULES)
                                dir.resolve(ANDROID_LEGACY_PROGUARD_RULES_FILE_NAME).writeText(ANDROID_LEGACY_PROGUARD_RULES)
                                val androidCurrentProguardRules = if (legacyAndroidCompatibility) {
                                    ANDROID_LEGACY_PROGUARD_RULES
                                } else {
                                    ANDROID_PROGUARD_RULES
                                }
                                dir.resolve(ANDROID_CURRENT_PROGUARD_RULES_FILE_NAME).writeText(androidCurrentProguardRules)
                            }
                        }
                    }
                } else {
                    log.debug { "Decoroutinator plugin is disabled" }
                }
            }
        }
    }
}

internal fun interface GroovyDslInitializer {
    fun initGroovyDsl(target: Project)
}

private val groovyDslInitializer = ServiceLoader.load(GroovyDslInitializer::class.java).iterator().next()!!

private inline fun visitModuleInfoFiles(root: File, onModuleInfoFile: (path: File, relativePath: File) -> Unit) {
    root.walk().forEach { file ->
        if (file.isFile && file.isModuleInfo) {
            val relativePath = file.relativeTo(root)
            onModuleInfoFile(file, relativePath)
        }
    }
}

private val File.isModuleInfo: Boolean
    get() = name == MODULE_INFO_CLASS_NAME

internal val Project.decoroutinatorDir: File
    get() = layout.buildDirectory.dir("decoroutinator").get().asFile

private const val ANDROID_PROGUARD_RULES_FILE_NAME = "android.pro"
private const val ANDROID_LEGACY_PROGUARD_RULES_FILE_NAME = "android-legacy.pro"
internal const val ANDROID_CURRENT_PROGUARD_RULES_FILE_NAME = "android-current.pro"

private val ANDROID_PROGUARD_RULES = """
    # Decoroutinator ProGuard rules
    -keep,allowshrinking,allowobfuscation @kotlin.coroutines.jvm.internal.DebugMetadata class *
    -keepclassmembers @kotlin.coroutines.jvm.internal.DebugMetadata class * { int label; }
    -keep,allowshrinking @${DecoroutinatorTransformed::class.java.name} class *
    -keepclassmembers @${DecoroutinatorTransformed::class.java.name} class * {
         static *(${DecoroutinatorSpec::class.java.name}, ${Object::class.java.name});
    }
    -keep,allowobfuscation interface ${DecoroutinatorSpec::class.java.name} { *; }
    -keep @${AndroidKeep::class.java.name} class * { *; }
    
""".trimIndent()

private val ANDROID_LEGACY_PROGUARD_RULES = ANDROID_PROGUARD_RULES + """
    -keep @${AndroidLegacyKeep::class.java.name} class * { *; }
    
""".trimIndent()
