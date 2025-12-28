@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.gradleplugin

import org.gradle.api.Project
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import java.io.File
import java.util.Base64
import java.util.zip.ZipInputStream
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal const val ANDROID_CURRENT_PROGUARD_RULES_FILE_NAME = "android-current.pro"
internal const val EXTENSION_NAME = "stacktraceDecoroutinator"
internal const val ATTRIBUTE_EXTENSION_NAME = "stacktraceDecoroutinatorAttribute"

internal fun ArtifactBuilder.addJarClassesAndResources(jarBase64: String) {
    ZipInputStream(Base64.getDecoder().decode(jarBase64).inputStream()).use { input ->
        while (true) {
            val entry = input.nextEntry ?: break
            if (!entry.isDirectory && entry.name != "META-INF/MANIFEST.MF") {
                val path = entry.name.split("/")
                val body = input.readBytes()
                ensureDirAndAndAddFile(path, body.inputStream())
            }
        }
    }
}

internal object DecoroutinatorTransformedState {
    const val UNTRANSFORMED = "UNTRANSFORMED"
    const val TRANSFORMED = "TRANSFORMED"
    const val TRANSFORMED_SKIPPING_SPEC_METHODS = "TRANSFORMED_SKIPPING_SPEC_METHODS"
}

internal val decoroutinatorTransformedStateAttribute: Attribute<String> = Attribute.of(
    "dev.reformator.stacktracedecoroutinator.transformedState",
    String::class.java
)

internal object DecoroutinatorEmbeddedDebugProbesState {
    const val FALSE = "FALSE"
    const val TRUE = "TRUE"
}

internal val decoroutinatorEmbeddedDebugProbesAttribute: Attribute<String> = Attribute.of(
    "dev.reformator.stacktracedecoroutinator.embeddedDebugProbes",
    String::class.javaObjectType
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
    var artifactTypes = listOf(
        "aar",
        ArtifactTypeDefinition.JAR_TYPE,
        ArtifactTypeDefinition.JVM_CLASS_DIRECTORY,
        ArtifactTypeDefinition.ZIP_TYPE,
        "android-classes-directory",
        "android-classes-jar"
    )
    var artifactTypesForAarTransformation = setOf("aar")
    val embeddedDebugProbesConfigurations = StringMatcherProperty()
}

internal val Project.decoroutinatorDir: File
    get() = layout.buildDirectory.dir("decoroutinator").get().asFile

internal fun String.addVariant(variant: String): String {
    val suffix = lastIndexOf('.').let { index ->
        if (index == -1) "" else substring(index)
    }
    return "${removeSuffix(suffix)}-$variant$suffix"
}
