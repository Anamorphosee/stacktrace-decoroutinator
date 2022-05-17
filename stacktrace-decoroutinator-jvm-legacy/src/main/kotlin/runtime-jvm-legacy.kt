package dev.reformator.stacktracedecoroutinator.runtime

import dev.reformator.stacktracedecoroutinator.common.*
import dev.reformator.stacktracedecoroutinator.jvmcommon.loadDecoroutinatorBaseContinuationClassBody
import dev.reformator.stacktracedecoroutinator.jvmlegacy.getClassIfLoaded
import dev.reformator.stacktracedecoroutinator.jvmlegacy.loadClass
import dev.reformator.stacktracedecoroutinator.jvmlegacycommon.DecoroutinatorJvmLegacyStacktraceMethodHandleRegistry

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
    init {
        decoroutinatorRegistry = object: BaseDecoroutinatorRegistry() {
            override val stacktraceMethodHandleRegistry: DecoroutinatorStacktraceMethodHandleRegistry
                get() = DecoroutinatorJvmLegacyStacktraceMethodHandleRegistry
        }
    }

    fun getState(loader: ClassLoader = classLoader!!): DecoroutinatorRuntimeState {
        val baseContinuationClass = loader.getClassIfLoaded(BASE_CONTINUATION_CLASS_NAME)
        return when {
            baseContinuationClass == null -> DecoroutinatorRuntimeState.NOT_LOADED
            !baseContinuationClass.isDecoroutinatorBaseContinuation -> DecoroutinatorRuntimeState.UNAVAILABLE
            decoroutinatorRegistry.enabled -> DecoroutinatorRuntimeState.ENABLED
            else -> DecoroutinatorRuntimeState.DISABLED
        }
    }

    fun load(loader: ClassLoader = classLoader!!): DecoroutinatorRuntimeState {
        when (val state = getState(loader)) {
            DecoroutinatorRuntimeState.UNAVAILABLE -> throw IllegalStateException(
                "Cannot load stacktrace-decoroutinator runtime cause " +
                        "class [$BASE_CONTINUATION_CLASS_NAME] was already loaded"
            )
            DecoroutinatorRuntimeState.NOT_LOADED -> Unit
            else -> return state
        }
        loader.loadClass(BASE_CONTINUATION_CLASS_NAME, loadDecoroutinatorBaseContinuationClassBody())
        return getState(loader)
    }
}
