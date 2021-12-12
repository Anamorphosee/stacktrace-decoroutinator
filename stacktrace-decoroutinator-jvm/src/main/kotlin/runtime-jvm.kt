package dev.reformator.stacktracedecoroutinator.runtime

import dev.reformator.stacktracedecoroutinator.continuation.DecoroutinatorRuntimeMarker
import dev.reformator.stacktracedecoroutinator.registry.DecoroutinatorRegistryImpl
import dev.reformator.stacktracedecoroutinator.utils.classLoader
import dev.reformator.stacktracedecoroutinator.utils.getClassIfLoaded
import dev.reformator.stacktracedecoroutinator.utils.loadClass
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import java.nio.file.FileSystems

enum class DecoroutinatorRuntimeState {
    /**
     * Stacktrace-decoroutinator runtime was not loaded and no one coroutine was created.
     */
    NOT_LOADED,

    /**
     * Stacktrace-decoroutinator runtime was not loaded and one or more coroutine was created.
     * At that state Stacktrace-decoroutinator runtime cannot be loaded.
     */
    UNAVAILABLE,

    /**
     * Stacktrace-decoroutinator runtime was loaded and enabled.
     */
    ENABLED,

    /**
     * Stacktrace-decoroutinator runtime was loaded but disabled by the system property.
     */
    DISABLED
}

//not getting by reflection because it has not to lead to loading the class
private const val BASE_CONTINUATION_CLASS_NAME = "kotlin.coroutines.jvm.internal.BaseContinuationImpl"

object DecoroutinatorRuntime {
    fun getState(loader: ClassLoader = classLoader!!): DecoroutinatorRuntimeState {
        val baseContinuationClass = loader.getClassIfLoaded(BASE_CONTINUATION_CLASS_NAME)
        return when {
            baseContinuationClass == null -> DecoroutinatorRuntimeState.NOT_LOADED
            baseContinuationClass.getAnnotation(DecoroutinatorRuntimeMarker::class.java) == null -> DecoroutinatorRuntimeState.UNAVAILABLE
            DecoroutinatorRegistryImpl.enabled -> DecoroutinatorRuntimeState.ENABLED
            else -> DecoroutinatorRuntimeState.DISABLED
        }
    }

    fun load(loader: ClassLoader = classLoader!!) {
        when (getState(loader)) {
            DecoroutinatorRuntimeState.UNAVAILABLE -> throw IllegalStateException(
                "Cannot load stacktrace-decoroutinator runtime cause class [$BASE_CONTINUATION_CLASS_NAME] was already loaded"
            )
            DecoroutinatorRuntimeState.NOT_LOADED -> Unit
            else -> return
        }

        val path = BASE_CONTINUATION_CLASS_NAME.replace(".", FileSystems.getDefault().separator) + ".class"
        val classBodyUrls = ClassLoader.getSystemResources(path)

        while (classBodyUrls.hasMoreElements()) {
            val classBody = classBodyUrls.nextElement().openStream().readBytes()

            val classReader = ClassReader(classBody)
            val classNode = ClassNode(Opcodes.ASM9)
            classReader.accept(classNode, ClassReader.SKIP_CODE or ClassReader.SKIP_FRAMES
                    or ClassReader.SKIP_DEBUG)

            val decoroutinatorRuntimeMarker =
                classNode.visibleAnnotations.orEmpty().find {
                    it.desc == Type.getDescriptor(DecoroutinatorRuntimeMarker::class.java)
                } != null

            if (decoroutinatorRuntimeMarker) {
                loader.loadClass(BASE_CONTINUATION_CLASS_NAME, classBody)
                return
            }
        }

        throw IllegalStateException("Class [$BASE_CONTINUATION_CLASS_NAME] with " +
                "annotation [${DecoroutinatorRuntimeMarker::class}] was not found")
    }
}
