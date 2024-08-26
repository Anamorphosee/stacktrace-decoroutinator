@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("DecoroutinatorAgentKt")

package dev.reformator.stacktracedecoroutinator.jvmagent

import dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal.addDecoroutinatorTransformer
import java.lang.instrument.Instrumentation

fun premain(args: String?, inst: Instrumentation) {
    addDecoroutinatorTransformer(inst)
}
