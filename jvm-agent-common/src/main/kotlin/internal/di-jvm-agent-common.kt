@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal

import dev.reformator.bytecodeprocessor.intrinsics.GetOwnerClass
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.runtimesettings.DecoroutinatorMetadataInfoResolveStrategy
import dev.reformator.stacktracedecoroutinator.runtimesettings.internal.getRuntimeSettingsValue

internal val isBaseContinuationRedefinitionAllowed =
    getRuntimeSettingsValue({ isBaseContinuationRedefinitionAllowed }) {
        System.getProperty(
            "dev.reformator.stacktracedecoroutinator.isBaseContinuationRedefinitionAllowed",
            "true"
        ).toBoolean()
    }

internal val isRedefinitionAllowed =
    getRuntimeSettingsValue({ isRedefinitionAllowed }) {
        System.getProperty(
            "dev.reformator.stacktracedecoroutinator.isRedefinitionAllowed",
            "false"
        ).toBoolean()
    }

internal val metadataInfoResolveStrategy =
    getRuntimeSettingsValue({ metadataInfoResolveStrategy }) {
        DecoroutinatorMetadataInfoResolveStrategy.valueOf(System.getProperty(
            "dev.reformator.stacktracedecoroutinator.metadataInfoResolveStrategy",
            DecoroutinatorMetadataInfoResolveStrategy.SYSTEM_RESOURCE_AND_CLASS.name
        ))
    }.resolveFunction

val diClass: Class<*>
    @GetOwnerClass get() { fail() }
