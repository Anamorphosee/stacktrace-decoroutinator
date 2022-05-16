package dev.reformator.stacktracedecoroutinator.runtime

import dev.reformator.stacktracedecoroutinator.common.DecoroutinatorAgentRegistry
import net.bytebuddy.agent.ByteBuddyAgent
import java.nio.file.Files
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.createTempDirectory

object DecoroutinatorRuntime {
    private val lock = ReentrantLock()

    fun load() {
        lock.withLock {
            if (DecoroutinatorAgentRegistry.isDecoroutinatorAgentInstalled) {
                return
            }
            val agent = createTempDirectory().resolve("agent.jar")
            ClassLoader.getSystemResource("decoroutinatorAgentJar.bin").openStream().use {
                Files.copy(it, agent)
            }
            ByteBuddyAgent.attach(agent.toFile(), ByteBuddyAgent.ProcessProvider.ForCurrentVm.INSTANCE)
        }
        if (!DecoroutinatorAgentRegistry.isDecoroutinatorAgentInstalled) {
            throw IllegalStateException("Failed to load DecoroutinatorRuntime.")
        }
    }
}
