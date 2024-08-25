@file:Suppress("PackageDirectoryMismatch")

package org.gradle.kotlin.dsl

import org.gradle.api.Project
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.util.*

val decoroutinatorVersions: Map<String, String> = run {
    fun tryLoad(path: String): Map<String, String>? =
        try {
            FileInputStream("$path/versions.properties")
        } catch (_: FileNotFoundException) {
            null
        }?.use { input ->
            val properties = Properties()
            properties.load(input)
            properties.mapKeys { (key, _) -> key.toString() }.mapValues { (_, value) -> value.toString() }
        }
    tryLoad(".") ?: tryLoad("..")!!
}
