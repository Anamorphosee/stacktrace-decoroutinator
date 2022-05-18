package dev.reformator.stacktracedecoroutinator.jvm

import dev.reformator.stacktracedecoroutinator.common.DecoroutinatorStacktraceElement
import dev.reformator.stacktracedecoroutinator.common.DecoroutinatorStacktraceMethodHandleRegistry
import dev.reformator.stacktracedecoroutinator.jvmagentcommon.DecoroutinatorJvmAgentStacktraceMethodHandleRegistry
import dev.reformator.stacktracedecoroutinator.jvmagentcommon.decoroutinatorJvmAgentRegistry
import dev.reformator.stacktracedecoroutinator.jvmagentcommon.isDecoroutinatorAgentTransformed
import dev.reformator.stacktracedecoroutinator.jvmlegacycommon.DecoroutinatorJvmLegacyStacktraceMethodHandleRegistry
import java.lang.invoke.MethodHandle

object DecorountinatorJvmStacktraceMethodHandleRegistry: DecoroutinatorStacktraceMethodHandleRegistry {
    override fun getStacktraceMethodHandles(
        elements: Collection<DecoroutinatorStacktraceElement>
    ): Map<DecoroutinatorStacktraceElement, MethodHandle> = buildMap {
        elements.groupBy { it.className }.forEach { (className, elements) ->
            val clazz = Class.forName(className)
            if (tryAgentMethodHandleRegistry(clazz, elements)) {
                return@forEach
            }
            if (decoroutinatorJvmAgentRegistry.isRetransformationAllowed) {
                decoroutinatorJvmAgentRegistry.retransform(clazz)
                if (tryAgentMethodHandleRegistry(clazz, elements)) {
                    return@forEach
                }
            }
            putAll(DecoroutinatorJvmLegacyStacktraceMethodHandleRegistry.getStacktraceMethodHandles(elements))
        }
    }
}

private fun MutableMap<DecoroutinatorStacktraceElement, MethodHandle>.tryAgentMethodHandleRegistry(
    clazz: Class<*>,
    elements: List<DecoroutinatorStacktraceElement>
): Boolean =
    if (clazz.isDecoroutinatorAgentTransformed) {
        putAll(DecoroutinatorJvmAgentStacktraceMethodHandleRegistry.getStacktraceMethodHandles(elements))
        true
    } else {
        false
    }
