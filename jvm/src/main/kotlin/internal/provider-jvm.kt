@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.jvm.internal

import dev.reformator.stacktracedecoroutinator.common.internal.CommonSettingsProvider
import dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal.JvmAgentCommonSettingsProvider

internal var cachedRecoveryExplicitStacktrace: Boolean = CommonSettingsProvider.recoveryExplicitStacktrace
internal var cachedTailCallDeoptimize: Boolean = CommonSettingsProvider.tailCallDeoptimize
internal var cachedRecoveryExplicitStacktraceTimeoutMs: Long =
    CommonSettingsProvider.recoveryExplicitStacktraceTimeoutMs

internal class CommonSettingsProviderImpl: CommonSettingsProvider {
    override val recoveryExplicitStacktrace: Boolean
        get() = cachedRecoveryExplicitStacktrace

    override val tailCallDeoptimize: Boolean
        get() = cachedTailCallDeoptimize

    override val recoveryExplicitStacktraceTimeoutMs: Long
        get() = cachedRecoveryExplicitStacktraceTimeoutMs
}

internal var cachedIsBaseContinuationRedefinitionAllowed: Boolean =
    JvmAgentCommonSettingsProvider.isBaseContinuationRedefinitionAllowed
internal var cachedIsRedefinitionAllowed: Boolean = JvmAgentCommonSettingsProvider.isRedefinitionAllowed

internal class JvmAgentCommonSettingsProviderImpl: JvmAgentCommonSettingsProvider {
    override val isBaseContinuationRedefinitionAllowed: Boolean
        get() = cachedIsBaseContinuationRedefinitionAllowed

    override val isRedefinitionAllowed: Boolean
        get() = cachedIsRedefinitionAllowed
}
