@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("CommonGradlePluginKt")

package dev.reformator.stacktracedecoroutinator.gradleplugin

import dev.reformator.stacktracedecoroutinator.generator.internal.addReadProviderModuleToModuleInfo
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
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

private val log = KotlinLogging.logger { }

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

    fun updateIfNotSet(setter: (T) -> T) {
        if (!set) {
            _value = setter(_value)
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
    internal val _artifactTypes = ObservableProperty(setOf<String>())
    val regularDependencyConfigurations = StringMatcherProperty()
    val androidDependencyConfigurations = StringMatcherProperty()
    val jvmDependencyConfigurations = StringMatcherProperty()
    val jvmRuntimeDependencyConfigurations = StringMatcherProperty()
    val androidRuntimeDependencyConfigurations = StringMatcherProperty()
    val transformedClassesConfigurations = StringMatcherProperty()
    val transformedClassesSkippingSpecMethodsConfigurations = StringMatcherProperty()
    val tasks = StringMatcherProperty()
    val tasksSkippingSpecMethods = StringMatcherProperty()
    var artifactTypes: Set<String> by _artifactTypes
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
        isAndroid -> emptySet()
        else -> setOf("implementation")
    }
//    val androidConfigurations = when {
//        isKmp && androidTestsOnly -> setOf(
//            "androidTestImplementation",
//            "androidInstrumentedTestImplementation"
//        )
//        isKmp -> setOf("androidMainImplementation")
//        isAndroid && androidTestsOnly -> setOf("androidTestImplementation", "testImplementation")
//        isAndroid -> setOf("implementation")
//        else -> setOf()
//    }
//    val jvmConfigurations = when {
//        isKmp && jvmTestsOnly -> setOf("desktopTestImplementation", "androidUnitTestImplementation")
//        isKmp -> setOf("desktopMainImplementation", "androidUnitTestImplementation")
//        isAndroid -> setOf(".*UnitTestImplementation", "unitTestImplementation")
//        jvmTestsOnly -> setOf("testImplementation")
//        else -> setOf("implementation")
//    }
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
                )
            }
        }
        isAndroid -> {
            if (androidTestsOnly) {
                setOf(".*AndroidTestRuntimeClasspath", "androidTestRuntimeClasspath")
            } else {
                setOf(".*RuntimeClasspath", "runtimeClasspath")
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

private fun DecoroutinatorPluginExtension.setupLowLevelArtifactTypesConfig() {
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

private fun DecoroutinatorPluginExtension.setupLowLevelConfig(project: Project) {
    setupHighLevelConfig(project)
    setupLowLevelDependencyConfig(project)
    setupLowLevelRuntimeTransformedClassesConfig(project)
    if (useTransformedClassesForCompilation) {
        setupLowLevelCompileTransformedClassesConfig(project)
    }
    setupLowLevelTasksConfig(project)
    setupLowLevelArtifactTypesConfig()
    setupLowLevelEmbeddedDebugProbesConfigurations(project)
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
                        configurations.all { config ->
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

                    //runtime settings dependency
                    run {
                        val matcher = pluginExtension.runtimeSettingsDependencyConfigurations.matcher
                        configurations.all { config ->
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
                        val matcher = pluginExtension.embeddedDebugProbesConfigurations.matcher
                        configurations.all { config ->
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
