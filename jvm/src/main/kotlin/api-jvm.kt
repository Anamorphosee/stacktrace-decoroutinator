@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.jvm

import dev.reformator.stacktracedecoroutinator.common.DecoroutinatorCommonApi
import dev.reformator.stacktracedecoroutinator.common.DecoroutinatorStatus
import dev.reformator.stacktracedecoroutinator.common.internal.BASE_CONTINUATION_CLASS_NAME
import dev.reformator.stacktracedecoroutinator.common.internal.isTransformed
import dev.reformator.stacktracedecoroutinator.jvm.internal.CommonSettingsProviderImpl
import dev.reformator.stacktracedecoroutinator.jvm.internal.JvmAgentCommonSettingsProviderImpl
import dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal.addDecoroutinatorTransformer
import net.bytebuddy.agent.ByteBuddyAgent
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object DecoroutinatorJvmApi {
    fun install(
        recoveryExplicitStacktrace: Boolean = true,
        isBaseContinuationRedefinitionAllowed: Boolean = true,
        isRedefinitionAllowed: Boolean = false,
        tailCallDeoptimize: Boolean = true,
    ) {
        CommonSettingsProviderImpl.recoveryExplicitStacktrace = recoveryExplicitStacktrace
        CommonSettingsProviderImpl.tailCallDeoptimize = tailCallDeoptimize
        JvmAgentCommonSettingsProviderImpl.isBaseContinuationRedefinitionAllowed = isBaseContinuationRedefinitionAllowed
        JvmAgentCommonSettingsProviderImpl.isRedefinitionAllowed = isRedefinitionAllowed
        lock.withLock {
            if (!initialized) {
                val inst = ByteBuddyAgent.install()
                addDecoroutinatorTransformer(inst)
                initialized = true
            }
        }
        val baseContinuation = Class.forName(BASE_CONTINUATION_CLASS_NAME)
        if (!baseContinuation.isTransformed) {
            ByteBuddyAgent.install().retransformClasses(baseContinuation)
        }
        if (!baseContinuation.isTransformed) {
            error("Cannot install Decoroutinator runtime " +
                    "because class [$BASE_CONTINUATION_CLASS_NAME] is already loaded " +
                    "and class retransformations is not allowed.")
        }
    }

    fun getStatus(
        sourceCall: suspend (callThisAndReturnItsResult: suspend () -> Any?) -> Any? = { it() },
        allowTailCallOptimization: Boolean = false,
    ): DecoroutinatorStatus =
        DecoroutinatorCommonApi.getStatus(
            sourceCall = sourceCall,
            allowTailCallOptimization = allowTailCallOptimization
        )

    private val lock = ReentrantLock()
    private var initialized = false
}


