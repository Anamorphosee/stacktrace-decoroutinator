package dev.reformator.stacktracedecoroutinator.util

import dev.reformator.stacktracedecoroutinator.analyzer.ClassBodyResolver
import dev.reformator.stacktracedecoroutinator.analyzer.DefaultClassBodyResolver
import dev.reformator.stacktracedecoroutinator.continuation.DecoroutinatorRuntime
import jdk.nashorn.internal.codegen.types.Type
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.lang.invoke.MethodHandle
import java.util.function.BiFunction
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

fun callStack(
    stackHandlers: Array<MethodHandle>,
    stackLineNumbers: IntArray,
    invokeFunction: BiFunction<Int, Any?, Any?>,
    result: Any?
): Any? {
    val zeroResult = if (stackHandlers.isNotEmpty()) {
        stackHandlers[0].invokeExact(stackHandlers, stackLineNumbers, 1, invokeFunction, result, COROUTINE_SUSPENDED)
    } else {
        result
    }
    return if (zeroResult !== COROUTINE_SUSPENDED) {
        invokeFunction.apply(0, zeroResult)
    } else {
        COROUTINE_SUSPENDED
    }
}

fun <T1, T2> pairComparator(c1: Comparator<T1>, c2: Comparator<T2>): Comparator<Pair<T1, T2>> =
    Comparator.comparing({ p: Pair<T1, T2> -> p.first }, c1).thenComparing({ p -> p.second }, c2)

private val pairComparator: Comparator<Pair<Comparable<Any>, Comparable<Any>>> =
    pairComparator(naturalOrder(), naturalOrder())

@Suppress("UNCHECKED_CAST")
fun <T1: Comparable<T1>, T2: Comparable<T2>> pairComparator() = pairComparator as Comparator<Pair<T1, T2>>

abstract class JavaUtil {
    abstract fun retrieveResultValue(result: Result<*>): Any?
}

val Result<*>.value: Any?
    inline get() = JavaUtilImpl.instance.retrieveResultValue(this)

inline fun probeCoroutineResumed(frame: Continuation<*>) {
    JavaUtilImpl.probeCoroutineResumed(frame)
}

inline val Any.classLoader: ClassLoader?
get() = this.javaClass.classLoader

fun ClassLoader.getClassLoadingLock(className: String): Any {
    val method = ClassLoader::class.java.getDeclaredMethod("getClassLoadingLock", String::class.java)
    method.isAccessible = true
    return method.invoke(this, className)
}

fun ClassLoader.getClassIfLoaded(className: String): Class<*>? {
    val method = ClassLoader::class.java.getDeclaredMethod("findLoadedClass", String::class.java)
    method.isAccessible = true
    return synchronized(getClassLoadingLock(className)) {
        method.invoke(this, className) as Class<*>?
    }
}

fun ClassLoader.loadClass(className: String, classBody: ByteArray): Class<*> {
    val method = ClassLoader::class.java.getDeclaredMethod("defineClass", String::class.java,
        ByteArray::class.java, Integer.TYPE, Integer.TYPE)!!
    method.isAccessible = true
    return synchronized(getClassLoadingLock(className)) {
        method.invoke(this, className, classBody, 0, classBody.size) as Class<*>
    }
}

private val DECOROUTINATOR_RUNTIME_INTERNAL_CLASS_NAME = Type.getInternalName(DecoroutinatorRuntime::class.java)
private val DECOROUTINATOR_RUNTIME_DESC = "L$DECOROUTINATOR_RUNTIME_INTERNAL_CLASS_NAME;"
//not getting by generic because it has not to lead to load the class
private const val BASE_CONTINUATION_CLASS_NAME = "kotlin.coroutines.jvm.internal.BaseContinuationImpl"

enum class DocoroutinatorRuntimeStates {
    NOT_LOADED, ENABLED, DISABLED
}

class DocoroutinatorRuntime(
    private val classBodyResolver: ClassBodyResolver = DefaultClassBodyResolver()
) {
    fun enableDecoroutinatorRuntime() {
        when (state) {
            DocoroutinatorRuntimeStates.ENABLED -> return
            DocoroutinatorRuntimeStates.DISABLED -> throw IllegalStateException(
                "DocoroutinatorRuntime cannot be enabled because class $BASE_CONTINUATION_CLASS_NAME is already loaded"
            )
        }
        loadBaseContinuation(true)
    }

    fun disableDecoroutinatorRuntime() {
        when (state) {
            DocoroutinatorRuntimeStates.ENABLED -> throw IllegalStateException(
                "DocoroutinatorRuntime cannot be disabled because class $BASE_CONTINUATION_CLASS_NAME is already loaded"
            )
            DocoroutinatorRuntimeStates.DISABLED -> return
        }
        loadBaseContinuation(false)
    }

    val state: DocoroutinatorRuntimeStates
    get() {
        val clazz = this.classLoader!!.getClassIfLoaded(BASE_CONTINUATION_CLASS_NAME)
        return when {
            clazz == null -> DocoroutinatorRuntimeStates.NOT_LOADED
            clazz.getAnnotation(DecoroutinatorRuntime::class.java) != null -> DocoroutinatorRuntimeStates.ENABLED
            else -> DocoroutinatorRuntimeStates.DISABLED
        }
    }

    private fun loadBaseContinuation(enableDocoroutinatorRuntime: Boolean) {
        val classBodies =
            classBodyResolver.getClassBodies(BASE_CONTINUATION_CLASS_NAME)
        for (classBody in classBodies) {
            val classReader = ClassReader(classBody)
            val classNode = ClassNode()
            classReader.accept(classNode, ClassReader.SKIP_CODE or ClassReader.SKIP_FRAMES
                    or ClassReader.SKIP_DEBUG)
            val decoroutinatorRuntime =
                classNode.visibleAnnotations.orEmpty().find { it.desc == DECOROUTINATOR_RUNTIME_DESC } != null
            if (decoroutinatorRuntime == enableDocoroutinatorRuntime) {
                this.classLoader!!.loadClass(BASE_CONTINUATION_CLASS_NAME, classBody)
                return
            }
        }
        throw IllegalStateException(
            "class $BASE_CONTINUATION_CLASS_NAME " +
            "${if (enableDocoroutinatorRuntime) "with" else "without"}" +
            " DecoroutinatorRuntime annotation is not found"
        )
    }
}