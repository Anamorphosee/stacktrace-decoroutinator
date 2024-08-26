@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal

import java.util.ServiceLoader

internal val settingsProvider = ServiceLoader.load(JvmAgentCommonSettingsProvider::class.java).firstOrNull() ?:
    object: JvmAgentCommonSettingsProvider {}

internal val isBaseContinuationRedefinitionAllowed = settingsProvider.isBaseContinuationRedefinitionAllowed

internal val isRedefinitionAllowed = settingsProvider.isRedefinitionAllowed

internal val metadataInfoResolveStrategy = settingsProvider.metadataInfoResolveStrategy
