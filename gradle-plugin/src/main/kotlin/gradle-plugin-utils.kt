@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.gradleplugin

import java.util.Base64
import java.util.zip.ZipInputStream

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
