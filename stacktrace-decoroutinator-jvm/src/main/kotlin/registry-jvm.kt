package dev.reformator.stacktracedecoroutinator.registry

internal object DecoroutinatorRegistryImpl: BaseDecoroutinatorRegistry() {
    override val stacktraceMethodHandleRegistry
    get() = DecoroutinatorStacktraceMethodHandleRegistryImpl
}
