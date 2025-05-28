@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.jvm

import dev.reformator.stacktracedecoroutinator.common.DecoroutinatorCommonApi
import dev.reformator.stacktracedecoroutinator.common.DecoroutinatorStatus
import dev.reformator.stacktracedecoroutinator.common.internal.BASE_CONTINUATION_CLASS_NAME
import dev.reformator.stacktracedecoroutinator.common.internal.CommonSettingsProvider
import dev.reformator.stacktracedecoroutinator.common.internal.isTransformed
import dev.reformator.stacktracedecoroutinator.jvm.internal.cachedIsBaseContinuationRedefinitionAllowed
import dev.reformator.stacktracedecoroutinator.jvm.internal.cachedIsRedefinitionAllowed
import dev.reformator.stacktracedecoroutinator.jvm.internal.cachedMethodsNumberThreshold
import dev.reformator.stacktracedecoroutinator.jvm.internal.cachedRecoveryExplicitStacktrace
import dev.reformator.stacktracedecoroutinator.jvm.internal.cachedRecoveryExplicitStacktraceTimeoutMs
import dev.reformator.stacktracedecoroutinator.jvm.internal.cachedTailCallDeoptimize
import dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal.JvmAgentCommonSettingsProvider
import dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal.addDecoroutinatorTransformer
import net.bytebuddy.agent.ByteBuddyAgent
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object DecoroutinatorJvmApi {
    fun install(
        recoveryExplicitStacktrace: Boolean = CommonSettingsProvider.recoveryExplicitStacktrace,
        isBaseContinuationRedefinitionAllowed: Boolean =
            JvmAgentCommonSettingsProvider.isBaseContinuationRedefinitionAllowed,
        isRedefinitionAllowed: Boolean = JvmAgentCommonSettingsProvider.isRedefinitionAllowed,
        tailCallDeoptimize: Boolean = CommonSettingsProvider.tailCallDeoptimize,
        recoveryExplicitStacktraceTimeoutMs: UInt = CommonSettingsProvider.recoveryExplicitStacktraceTimeoutMs,
        methodsNumberThreshold: Int = CommonSettingsProvider.methodsNumberThreshold
    ) {
        cachedRecoveryExplicitStacktrace = recoveryExplicitStacktrace
        cachedTailCallDeoptimize = tailCallDeoptimize
        cachedRecoveryExplicitStacktraceTimeoutMs = recoveryExplicitStacktraceTimeoutMs
        cachedIsBaseContinuationRedefinitionAllowed = isBaseContinuationRedefinitionAllowed
        cachedIsRedefinitionAllowed = isRedefinitionAllowed
        cachedMethodsNumberThreshold = methodsNumberThreshold
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

    @Suppress("unused")
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


