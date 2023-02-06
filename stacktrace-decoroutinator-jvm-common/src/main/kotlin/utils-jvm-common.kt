package dev.reformator.stacktracedecoroutinator.jvmcommon

@Suppress("ClassName")
private class _jvmcommonStub

fun loadDecoroutinatorBaseContinuationClassBody(): ByteArray =
    loadResource("decoroutinatorBaseContinuation.class")!!

fun loadResource(name: String): ByteArray? {
    val classLoader = try {
        Thread.currentThread().contextClassLoader
    } catch (_: Throwable) {
        null
    } ?: try {
        _jvmcommonStub::class.java.classLoader
    } catch (_: Throwable) {
        null
    } ?: try {
        ClassLoader.getSystemClassLoader()
    } catch (_: Throwable) {
        null
    }
    val stream = if (classLoader != null) {
        classLoader.getResourceAsStream(name)
    } else {
        ClassLoader.getSystemResourceAsStream(name)
    }
    return stream?.use {
        it.readBytes()
    }
}
