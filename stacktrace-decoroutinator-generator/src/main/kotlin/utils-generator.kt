@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.generator

fun loadResource(name: String): ByteArray? {
    val resource = try {
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
    return resource?.use {
        it.readBytes()
    }
}

internal fun loadDecoroutinatorBaseContinuationClassBody(): ByteArray =
    loadResource("dev.reformator.stacktracedecoroutinator.decoroutinatorBaseContinuation.class")!!

@Suppress("ClassName")
private class _jvmcommonStub

internal interface JavaUtils {
    val metadataAnnotationClass: Class<*>
    fun getDebugMetadataInfo(clazz: Class<*>): DebugMetadataInfo?

    companion object: JavaUtils by JavaUtilsImpl()
}
