package dev.reformator.stacktracedecoroutinator.registry

import dev.reformator.stacktracedecoroutinator.generator.DecoroutinatorStacktraceClassGeneratorImpl

internal object DecoroutinatorRegistryImpl: DecoroutinatorRegistry {
    override val stacktraceMethodHandleRegistry =
        DecoroutinatorStacktraceMethodHandleRegistryImpl(DecoroutinatorStacktraceClassGeneratorImpl)
}
