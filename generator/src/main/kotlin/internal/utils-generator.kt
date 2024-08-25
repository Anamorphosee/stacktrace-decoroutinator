@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.generator.internal

import java.io.InputStream

fun getResourceAsStream(name: String): InputStream? =
    try {
        Thread.currentThread().contextClassLoader.getResourceAsStream(name)
    } catch (_: Throwable) {
        null
    } ?: try {
        _jvmcommonStub::class.java.classLoader.getResourceAsStream(name)
    } catch (_: Throwable) {
        null
    } ?: try {
        ClassLoader.getSystemClassLoader().getResourceAsStream(name)
    } catch (_: Throwable) {
        null
    } ?: try {
        ClassLoader.getSystemResourceAsStream(name)
    } catch (_: Throwable) {
        null
    }

fun loadResource(name: String): ByteArray? =
    getResourceAsStream(name)?.use { it.readBytes() }

internal val String.internalName: String
    get() = replace('.', '/')


@Suppress("ClassName")
private class _jvmcommonStub
