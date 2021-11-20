package dev.reformator.stacktracedecoroutinator.util

import java.lang.invoke.MethodHandle
import java.util.function.Function

inline fun MethodHandle.callStack(
    stackHandlers: Array<MethodHandle>,
    stackLineNumbers: IntArray,
    nextStep: Int,
    continuationInvokeMethodHandleFunc: Function<Int, MethodHandle>,
    result: Any?
): Any? =
    this.invokeExact(stackHandlers, stackLineNumbers, nextStep, continuationInvokeMethodHandleFunc, result,
        kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED)

fun <T1, T2> pairComparator(c1: Comparator<T1>, c2: Comparator<T2>): Comparator<Pair<T1, T2>> =
    Comparator.comparing({ p: Pair<T1, T2> -> p.first }, c1).thenComparing({ p -> p.second }, c2)

private val pairComparator: Comparator<Pair<Comparable<Any>, Comparable<Any>>> =
    pairComparator(naturalOrder(), naturalOrder())

@Suppress("UNCHECKED_CAST")
fun <T1: Comparable<T1>, T2: Comparable<T2>> pairComparator() = pairComparator as Comparator<Pair<T1, T2>>