@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.jvm.internal

import dev.reformator.stacktracedecoroutinator.common.internal.CommonSettingsProvider
import dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal.JvmAgentCommonSettingsProvider

internal class CommonSettingsProviderImpl: CommonSettingsProvider {
    companion object {
        var recoveryExplicitStacktrace: Boolean = false
    }

    override val recoveryExplicitStacktrace: Boolean
        get() = CommonSettingsProviderImpl.recoveryExplicitStacktrace
}

internal class JvmAgentCommonSettingsProviderImpl: JvmAgentCommonSettingsProvider {
    companion object {
        var isBaseContinuationRedefinitionAllowed: Boolean = false
        var isRedefinitionAllowed: Boolean = false
    }

    override val isBaseContinuationRedefinitionAllowed: Boolean
        get() = JvmAgentCommonSettingsProviderImpl.isBaseContinuationRedefinitionAllowed

    override val isRedefinitionAllowed: Boolean
        get() = JvmAgentCommonSettingsProviderImpl.isRedefinitionAllowed
}
