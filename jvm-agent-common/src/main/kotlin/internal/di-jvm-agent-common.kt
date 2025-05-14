@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal

import dev.reformator.stacktracedecoroutinator.common.internal.loadService

internal val settingsProvider = loadService<JvmAgentCommonSettingsProvider>() ?:
    object: JvmAgentCommonSettingsProvider {}

internal val isBaseContinuationRedefinitionAllowed = settingsProvider.isBaseContinuationRedefinitionAllowed

internal val isRedefinitionAllowed = settingsProvider.isRedefinitionAllowed

internal val metadataInfoResolveStrategy = settingsProvider.metadataInfoResolveStrategy
