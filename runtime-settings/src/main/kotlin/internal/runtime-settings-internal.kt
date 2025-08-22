@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.runtimesettings.internal

import dev.reformator.stacktracedecoroutinator.runtimesettings.DecoroutinatorRuntimeSettingsProvider
import java.util.ServiceLoader

@Suppress("ObjectInheritsException", "JavaIoSerializableObjectMustHaveReadResolve")
private object DefaultValueException: Exception()

internal fun defaultValue(): Nothing =
    throw DefaultValueException

sealed interface RuntimeSettingsValue<out T> {
    object Default: RuntimeSettingsValue<Nothing>
    class Value<out T>(val value: T): RuntimeSettingsValue<T>
}

private class RuntimeSettingsProviderWithPriority(
    val provider: DecoroutinatorRuntimeSettingsProvider,
    val priority: Int
)

private val runtimeSettingsProviderInstances =
    ServiceLoader.load(DecoroutinatorRuntimeSettingsProvider::class.java)
        .asSequence()
        .map { RuntimeSettingsProviderWithPriority(it, it.priority) }
        .sortedByDescending { it.priority }
        .toList()

fun <T> getRuntimeSettingsValue(get: DecoroutinatorRuntimeSettingsProvider.() -> T): RuntimeSettingsValue<T> {
    var index = 0
    val value = run {
        while (index < runtimeSettingsProviderInstances.size) {
            try {
                return@run runtimeSettingsProviderInstances[index].provider.get()
            } catch (_: DefaultValueException) {
                index++
            }
        }
        return RuntimeSettingsValue.Default
    }
    val provider = runtimeSettingsProviderInstances[index].provider
    val priority = runtimeSettingsProviderInstances[index].priority
    index++
    while (index < runtimeSettingsProviderInstances.size && runtimeSettingsProviderInstances[index].priority == priority) {
        try {
            val otherProvider = runtimeSettingsProviderInstances[index].provider
            val otherValue = otherProvider.get()
            if (otherValue != value) {
                error("different values with the same priority[$priority]: [$value] from [$provider] and [$otherValue] from [$otherProvider]")
            }
        }  catch (_: DefaultValueException) { }
        index++
    }
    return RuntimeSettingsValue.Value(value)
}

inline fun <T> getRuntimeSettingsValue(
    noinline get: DecoroutinatorRuntimeSettingsProvider.() -> T,
    default: () -> T
): T =
    when(val value = getRuntimeSettingsValue(get)) {
        is RuntimeSettingsValue.Value<T> -> value.value
        RuntimeSettingsValue.Default -> default()
    }
