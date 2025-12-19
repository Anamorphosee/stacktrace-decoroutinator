@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("AttributeGradlePluginKt")

package dev.reformator.stacktracedecoroutinator.gradleplugin

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.kotlin.dsl.stacktraceDecoroutinatorAttribute

private val log = KotlinLogging.logger { }

@Suppress("unused")
class DecoroutinatorAttributePlugin: Plugin<Project> {
    override fun apply(target: Project) {
        log.debug { "applying Decoroutinator attribute plugin to [${target.name}]" }
        with (target) {
            val pluginExtension = extensions.create(
                ::stacktraceDecoroutinatorAttribute.name,
                DecoroutinatorAttributePluginExtension::class.java
            )
            afterEvaluate { _ ->
                createUnsetDecoroutinatorTransformedStateAttributeAction(
                    artifactTypes = pluginExtension.artifactTypesForAttributes
                ).execute(target)
            }
        }
    }
}

open class DecoroutinatorAttributePluginExtension {
    var artifactTypesForAttributes = setOf(
        ArtifactTypeDefinition.JAR_TYPE,
        ArtifactTypeDefinition.JVM_CLASS_DIRECTORY,
        ArtifactTypeDefinition.ZIP_TYPE,
        "aar",
        "android-classes-directory",
        "android-classes-jar"
    )
}
