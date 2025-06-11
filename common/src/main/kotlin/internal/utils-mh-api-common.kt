@file:Suppress("NewApi", "PackageDirectoryMismatch")
@file:AndroidLegacyKeep

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import dev.reformator.stacktracedecoroutinator.provider.internal.AndroidLegacyKeep
import java.lang.invoke.MethodType
import kotlin.jvm.java

val specMethodType: MethodType = MethodType.methodType(
    Object::class.java,
    DecoroutinatorSpec::class.java,
    Object::class.java
)
