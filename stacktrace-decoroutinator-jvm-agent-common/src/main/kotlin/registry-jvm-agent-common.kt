package dev.reformator.stacktracedecoroutinator.jvmagentcommon

import dev.reformator.stacktracedecoroutinator.common.getFileClass
import org.objectweb.asm.Type
import java.lang.invoke.MethodHandles
import java.util.concurrent.ConcurrentHashMap

interface DecoroutinatorJvmAgentRegistry {
    val isBaseContinuationRetransformationAllowed: Boolean
        get() = false

    val isRetransformationAllowed: Boolean
        get() = false

    fun registerLookup(lookup: MethodHandles.Lookup)

    fun getLookup(clazz: Class<*>): MethodHandles.Lookup

    fun retransform(clazz: Class<*>) {
        error(
            "Class retransformation is not allowed. " +
            "Didn't you forget to add JVM agent 'stacktrace-decoroutinator-jvm-agent' to your JVM command line."
        )
    }
}

var decoroutinatorJvmAgentRegistry: DecoroutinatorJvmAgentRegistry = DecoroutinatorJvmAgentRegistryImpl()

open class DecoroutinatorJvmAgentRegistryImpl: DecoroutinatorJvmAgentRegistry {
    private val class2Lookup: MutableMap<Class<*>, MethodHandles.Lookup> = ConcurrentHashMap()

    override fun registerLookup(lookup: MethodHandles.Lookup) {
        class2Lookup[lookup.lookupClass()] = lookup
    }

    override fun getLookup(clazz: Class<*>): MethodHandles.Lookup =
        class2Lookup[clazz] ?: run {
            clazz.getDeclaredMethod(REGISTER_LOOKUP_METHOD_NAME).invoke(null)
            class2Lookup[clazz]!!
        }
}

@Target(AnnotationTarget.FUNCTION)
@Retention
private annotation class RegistryMarker

@RegistryMarker
internal fun registerLookup(lookup: MethodHandles.Lookup) {
    decoroutinatorJvmAgentRegistry.registerLookup(lookup)
}

private val fileClass = getFileClass { }
internal val registerLookupInternalClassName: String = Type.getInternalName(fileClass)
internal val registerLookupMethodName: String =
    fileClass.methods.first { it.isAnnotationPresent(RegistryMarker::class.java) }.name
