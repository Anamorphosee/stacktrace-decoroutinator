package dev.reformator.stacktracedecoroutinator.runtime

import dev.reformator.stacktracedecoroutinator.common.BASE_CONTINUATION_CLASS_NAME
import dev.reformator.stacktracedecoroutinator.common.decoroutinatorRegistry
import dev.reformator.stacktracedecoroutinator.common.isDecoroutinatorBaseContinuation
import dev.reformator.stacktracedecoroutinator.jvm.DecoroutinatorJvmRegistry
import dev.reformator.stacktracedecoroutinator.jvm.DecoroutinatorRuntimeJvmAgentRegistry
import dev.reformator.stacktracedecoroutinator.jvmagentcommon.DecoroutinatorClassFileTransformer
import dev.reformator.stacktracedecoroutinator.jvmagentcommon.decoroutinatorJvmAgentRegistry
import net.bytebuddy.agent.ByteBuddyAgent
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object DecoroutinatorRuntime {
    private val lock = ReentrantLock()
    private var initialized = false

    fun load() {
        lock.withLock {
            if (initialized) {
                return
            }
            val inst = ByteBuddyAgent.install()
            decoroutinatorRegistry = DecoroutinatorJvmRegistry
            decoroutinatorJvmAgentRegistry = DecoroutinatorRuntimeJvmAgentRegistry(inst)
            Class.forName("kotlin.Unit")
            inst.addTransformer(DecoroutinatorClassFileTransformer, inst.isRetransformClassesSupported)
            initialized = true
        }
        val baseContinuation = Class.forName(BASE_CONTINUATION_CLASS_NAME)
        if (baseContinuation.isDecoroutinatorBaseContinuation) {
            return
        }
        if (decoroutinatorJvmAgentRegistry.isBaseContinuationRetransformationAllowed) {
            decoroutinatorJvmAgentRegistry.retransform(baseContinuation)
        }
        if (!baseContinuation.isDecoroutinatorBaseContinuation) {
            error("Cannot load Decoroutinator runtime " +
                    "because class [$BASE_CONTINUATION_CLASS_NAME] is already loaded " +
                    "and class retransformations is not allowed.")
        }
    }
}
