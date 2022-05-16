package dev.reformator.stacktracedecoroutinator.jvmcommon

import dev.reformator.stacktracedecoroutinator.common.BASE_CONTINUATION_CLASS_NAME
import dev.reformator.stacktracedecoroutinator.common.DECOROUTINATOR_MARKER_CLASS_NAME
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.nio.file.FileSystems

val DECOROUTINATOR_MARKER_INTERNAL_CLASS_NAME = DECOROUTINATOR_MARKER_CLASS_NAME.replace('.', '/')
val DECOROUTINATOR_MARKER_CLASS_DESCRIPTOR = "L$DECOROUTINATOR_MARKER_INTERNAL_CLASS_NAME;"

fun loadDecoroutinatorBaseContinuationClassBody(): ByteArray {
    val path = BASE_CONTINUATION_CLASS_NAME.replace(".", FileSystems.getDefault().separator) + ".class"
    val classBodyUrls = ClassLoader.getSystemResources(path)
    while (classBodyUrls.hasMoreElements()) {
        val classBody = classBodyUrls.nextElement().openStream().readBytes()

        val classReader = ClassReader(classBody)
        val classNode = ClassNode(Opcodes.ASM9)
        classReader.accept(
            classNode,
            ClassReader.SKIP_CODE or ClassReader.SKIP_FRAMES or ClassReader.SKIP_DEBUG
        )

        val hasMarker = classNode.visibleAnnotations.orEmpty().find {
            it.desc == DECOROUTINATOR_MARKER_CLASS_DESCRIPTOR
        } != null

        if (hasMarker) {
            return classBody
        }
    }
    throw IllegalStateException(
        "Class [$BASE_CONTINUATION_CLASS_NAME] with annotation [$DECOROUTINATOR_MARKER_CLASS_NAME] was not found"
    )
}
