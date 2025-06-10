@file:Suppress("NewApi", "PackageDirectoryMismatch")
@file:DecoroutinatorLegacyAndroidKeep

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorLegacyAndroidKeep
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import java.lang.invoke.MethodType
import kotlin.jvm.java

val specMethodType: MethodType = MethodType.methodType(
    Object::class.java,
    DecoroutinatorSpec::class.java,
    Object::class.java
)
