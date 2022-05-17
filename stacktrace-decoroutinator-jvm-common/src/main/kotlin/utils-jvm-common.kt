package dev.reformator.stacktracedecoroutinator.jvmcommon

import dev.reformator.stacktracedecoroutinator.common.BASE_CONTINUATION_CLASS_NAME
import dev.reformator.stacktracedecoroutinator.common.DecoroutinatorMarker
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import java.nio.file.FileSystems

private val decoroutinatorMarkerClassDescriptor = Type.getDescriptor(DecoroutinatorMarker::class.java)

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
            it.desc == decoroutinatorMarkerClassDescriptor
        } != null

        if (hasMarker) {
            return classBody
        }
    }
    throw IllegalStateException(
        "Class [$BASE_CONTINUATION_CLASS_NAME] with annotation [${DecoroutinatorMarker::class.java}] was not found"
    )
}
