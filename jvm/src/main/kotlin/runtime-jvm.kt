@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.runtime

import dev.reformator.stacktracedecoroutinator.jvmagentcommon.*
import net.bytebuddy.agent.ByteBuddyAgent
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object DecoroutinatorRuntime {
    private val lock = ReentrantLock()
    private var initialized = false

    fun load() {
        lock.withLock {
            if (!initialized) {
                val inst = ByteBuddyAgent.install()
                addDecoroutinatorTransformer(inst)
                initialized = true
            }
        }
        val baseContinuation = Class.forName(BASE_CONTINUATION_CLASS_NAME)
        if (!baseContinuation.isDecoroutinatorBaseContinuation) {
            ByteBuddyAgent.install().retransformClasses(baseContinuation)
        }
        if (!baseContinuation.isDecoroutinatorBaseContinuation) {
            error("Cannot load Decoroutinator runtime " +
                    "because class [$BASE_CONTINUATION_CLASS_NAME] is already loaded " +
                    "and class retransformations is not allowed.")
        }
    }
}
