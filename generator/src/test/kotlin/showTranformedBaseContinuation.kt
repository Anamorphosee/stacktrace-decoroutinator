@file:Suppress("PackageDirectoryMismatch")

package showtransformedbasecontinuation

import dev.reformator.stacktracedecoroutinator.generator.internal.loadDecoroutinatorBaseContinuationClassBody
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.TraceClassVisitor
import java.io.PrintWriter

fun main() {
    val classReader = ClassReader(loadDecoroutinatorBaseContinuationClassBody())
    PrintWriter(System.out.writer()).use {
        classReader.accept(TraceClassVisitor(it), 0)
    }
}
