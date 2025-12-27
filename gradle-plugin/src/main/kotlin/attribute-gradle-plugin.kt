@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("AttributeGradlePluginKt")

package dev.reformator.stacktracedecoroutinator.gradleplugin

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.type.ArtifactTypeDefinition

private val log = KotlinLogging.logger { }

@Suppress("unused")
class DecoroutinatorAttributePlugin: Plugin<Project> {
    override fun apply(target: Project) {
        log.debug { "applying Decoroutinator attribute plugin to [${target.name}]" }
        with (target) {
            val pluginExtension = extensions.create(
                ATTRIBUTE_EXTENSION_NAME,
                DecoroutinatorAttributePluginExtension::class.java
            )
            afterEvaluate { _ ->
                buildUnsetTransformedStateAttributeActionForAllConfigurationsOutgoingVariants(
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

internal fun buildUnsetTransformedStateAttributeActionForAllConfigurationsOutgoingVariants(
    artifactTypes: Collection<String>
): Action<Project> = Action<Project> { project ->
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
