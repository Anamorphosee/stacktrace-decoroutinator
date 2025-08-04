@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.jvm.internal

import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorTransformed

internal val Class<*>.isTransformed: Boolean
    get() = getDeclaredAnnotation(DecoroutinatorTransformed::class.java) != null
