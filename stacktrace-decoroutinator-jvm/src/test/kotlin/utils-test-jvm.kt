package dev.reformator.stacktracedecoroutinator.utils

import dev.reformator.stacktracedecoroutinator.common.BASE_CONTINUATION_CLASS_NAME
import dev.reformator.stacktracedecoroutinator.common.DecoroutinatorMarker
import dev.reformator.stacktracedecoroutinator.common.getFileClass
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import java.nio.file.FileSystems
import kotlin.test.assertTrue

private val className = getFileClass {  }.name

fun checkStacktrace(vararg elements: StackTraceElement) {
    if (elements.isEmpty()) {
        return
    }
    Exception().stackTrace.also { stacktrace ->
        val startIndex = stacktrace.indexOfFirst { it eq elements[0] }
        elements.forEachIndexed { index, element ->
            assertTrue(element eq stacktrace[startIndex + index])
        }
    }
}

private infix fun StackTraceElement.eq(element: StackTraceElement) =
    this.className == element.className && this.methodName == element.methodName &&
            this.lineNumber == element.lineNumber

fun getLineNumber(): Int {
    val stacktrace = Exception().stackTrace
    val stacktraceIndex = stacktrace.indexOfFirst {
        it.className == className
    } + 1
    return stacktrace[stacktraceIndex].lineNumber
}

fun ClassLoader.getClassLoadingLock(className: String): Any {
    val method = ClassLoader::class.java.getDeclaredMethod("getClassLoadingLock", String::class.java)
    method.isAccessible = true
    return method.invoke(this, className)
}

fun ClassLoader.loadClass(className: String, classBody: ByteArray): Class<*> {
    val method = ClassLoader::class.java.getDeclaredMethod("defineClass", String::class.java,
        ByteArray::class.java, Integer.TYPE, Integer.TYPE)
    method.isAccessible = true
    return synchronized(getClassLoadingLock(className)) {
        method.invoke(this, className, classBody, 0, classBody.size) as Class<*>
    }
}

fun getStdlibBaseContinuationClassBody(): ByteArray {
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
            it.desc == Type.getDescriptor(DecoroutinatorMarker::class.java)
        } != null
        if (!hasMarker) {
            return classBody
        }
    }
    error("not found")
}
