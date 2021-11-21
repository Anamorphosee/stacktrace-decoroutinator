package dev.reformator.stacktracedecoroutinator.util

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.function.BiFunction
import kotlin.coroutines.Continuation
import kotlin.coroutines.jvm.internal.BaseContinuationImpl
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

val publicLookup: MethodHandles.Lookup = MethodHandles.publicLookup()

val getStackTraceElementHandle: MethodHandle = publicLookup.findStatic(
    Class.forName("kotlin.coroutines.jvm.internal.DebugMetadataKt"),
    "getStackTraceElement",
    MethodType.methodType(StackTraceElement::class.java, BaseContinuationImpl::class.java)
)

val probeCoroutineResumedHandle: MethodHandle = publicLookup.findStatic(
    Class.forName("kotlin.coroutines.jvm.internal.DebugProbesKt"),
    "probeCoroutineResumed",
    MethodType.methodType(Void.TYPE, Continuation::class.java)
)

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