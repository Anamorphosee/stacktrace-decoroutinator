@file:Suppress("PackageDirectoryMismatch")

package showbasecontinuation

import dev.reformator.stacktracedecoroutinator.common.internal.BASE_CONTINUATION_CLASS_NAME
import dev.reformator.stacktracedecoroutinator.generator.internal.loadResource
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.TraceClassVisitor
import java.io.PrintWriter

fun main() {
    val classReader = ClassReader(loadResource(BASE_CONTINUATION_CLASS_NAME.replace('.', '/') + ".class"))
    PrintWriter(System.out.writer()).use {
        classReader.accept(TraceClassVisitor(it), 0)
    }
}
