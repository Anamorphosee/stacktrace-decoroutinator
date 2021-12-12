package dev.reformator.stacktracedecoroutinator.registry

import dalvik.system.InMemoryDexClassLoader
import dev.reformator.stacktracedecoroutinator.utils.classLoader
import java.nio.ByteBuffer

object DecoroutinatorAndroidStacktraceMethodHandleRegistryImpl: BaseDecoroutinatorStacktraceMethodHandleRegistry() {
    override fun generateStacktraceClass(
        className: String,
        fileName: String?,
        classRevision: Int,
        methodName2LineNumbers: Map<String, Set<Int>>
    ): Class<*> {
        val body = classLoader!!.getResourceAsStream("test.dex").readBytes()
        return InMemoryDexClassLoader(ByteBuffer.wrap(body), ClassLoader.getSystemClassLoader())
            .loadClass("dev.reformator.stacktracedecoroutinator.test.TestClass")
    }
}
