package dev.reformator.stacktracedecoroutinator.common

internal object DecoroutinatorRegistryImpl: BaseDecoroutinatorRegistry() {
    override val stacktraceMethodHandleRegistry
        get() = DecoroutinatorStacktraceMethodHandleRegistryImpl
}
