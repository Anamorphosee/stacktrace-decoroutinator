@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("DecoroutinatorAgentKt")

package dev.reformator.stacktracedecoroutinator.jvmagent

import dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal.addDecoroutinatorTransformer
import java.lang.instrument.Instrumentation

fun premain(@Suppress("UNUSED_PARAMETER") args: String?, inst: Instrumentation) {
    addDecoroutinatorTransformer(inst)
}
