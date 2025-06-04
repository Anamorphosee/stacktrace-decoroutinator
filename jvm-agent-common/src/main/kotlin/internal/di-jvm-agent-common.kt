@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal

import dev.reformator.bytecodeprocessor.intrinsics.GetOwnerClass
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.common.internal.settingsProvider

internal val isBaseContinuationRedefinitionAllowed = settingsProvider!!.isBaseContinuationRedefinitionAllowed

internal val isRedefinitionAllowed = settingsProvider!!.isRedefinitionAllowed

internal val metadataInfoResolveStrategy = settingsProvider!!.metadataInfoResolveStrategy.resolveFunction

val diClass: Class<*>
    @GetOwnerClass(deleteAfterModification = true) get() { fail() }