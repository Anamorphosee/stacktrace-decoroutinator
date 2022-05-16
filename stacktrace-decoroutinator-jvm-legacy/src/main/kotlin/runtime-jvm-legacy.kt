package dev.reformator.stacktracedecoroutinator.runtime

import dev.reformator.stacktracedecoroutinator.continuation.DecoroutinatorRuntimeMarker
import dev.reformator.stacktracedecoroutinator.common.DecoroutinatorRegistryImpl
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
     * Stacktrace-decoroutinator runtime was loaded but disabled.
     */
    DISABLED
}




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



        loader.loadClass(BASE_CONTINUATION_CLASS_NAME, classBody)


    }
}
