package dev.reformator.stacktracedecoroutinator.registry

internal object DecoroutinatorRegistryImpl: DecoroutinatorRegistry {
    override val stacktraceMethodHandleRegistry
    get() = DecoroutinatorStacktraceMethodHandleRegistryImpl
}
