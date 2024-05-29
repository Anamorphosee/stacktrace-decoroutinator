package dev.reformator.stacktracedecoroutinator.runtime

import dalvik.system.BaseDexClassLoader
import dalvik.system.InMemoryDexClassLoader
import dev.reformator.stacktracedecoroutinator.android.DecoroutinatorAndroidStacktraceMethodHandleRegistryImpl
import dev.reformator.stacktracedecoroutinator.android.utils.baseContinuationDexBody
import dev.reformator.stacktracedecoroutinator.common.*
import java.lang.reflect.Field
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

object DecoroutinatorRuntime {
    private val instrumentedClassLoaders = HashSet<ClassLoader>()
    private val instrumentedClassLoadersLock = ReentrantReadWriteLock()

    init {
        decoroutinatorRegistry = object: BaseDecoroutinatorRegistry() {
            override val stacktraceMethodHandleRegistry: DecoroutinatorStacktraceMethodHandleRegistry
                get() = DecoroutinatorAndroidStacktraceMethodHandleRegistryImpl
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun load(loader: ClassLoader = classLoader!!) {
        if (!isLoaderInstrumented(loader)) {
            val pathListField: Field = BaseDexClassLoader::class.java.getDeclaredField("pathList")
            pathListField.isAccessible = true

            val pathList = pathListField[loader]

            val dexElementsField: Field = Class.forName("dalvik.system.DexPathList").getDeclaredField("dexElements")
            dexElementsField.isAccessible = true

            val dexElements = dexElementsField[pathList] as Array<Any>

            val decoroutinatorDexElements = run {
                val dexClassLoader = InMemoryDexClassLoader(ByteBuffer.wrap(baseContinuationDexBody), null)
                val dexPathList = pathListField[dexClassLoader]
                dexElementsField[dexPathList] as Array<Any>
            }

            val newDexElements =  decoroutinatorDexElements plusArray dexElements

            instrumentedClassLoadersLock.write {
                if (loader !in instrumentedClassLoaders) {
                    dexElementsField[pathList] = newDexElements
                    instrumentedClassLoaders.add(loader)
                }
            }
        }
        val baseContinuationClass = loader.loadClass(BASE_CONTINUATION_CLASS_NAME)
        if (!baseContinuationClass.isDecoroutinatorBaseContinuation) {
            throw IllegalStateException(
                "Stacktrace-decoroutinator runtime can not be loaded because BaseContinuationImpl was already loaded."
            )
        }
    }

    private fun isLoaderInstrumented(loader: ClassLoader) = instrumentedClassLoadersLock.read {
        loader in instrumentedClassLoaders
    }
}

private infix fun <T> Array<T>.plusArray(elements: Array<out T>) = this + elements
