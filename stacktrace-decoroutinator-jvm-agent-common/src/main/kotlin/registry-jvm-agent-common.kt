package dev.reformator.stacktracedecoroutinator.jvmagentcommon

import dev.reformator.stacktracedecoroutinator.common.BaseDecoroutinatorRegistry
import dev.reformator.stacktracedecoroutinator.common.DecoroutinatorStacktraceMethodHandleRegistry
import dev.reformator.stacktracedecoroutinator.common.getFileClass
import dev.reformator.stacktracedecoroutinator.jvmcommon.loadResource
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import java.lang.instrument.Instrumentation
import java.lang.invoke.MethodHandles
import java.util.concurrent.ConcurrentHashMap

enum class DecoroutinatorJvmAgentDebugMetadataInfoResolveStrategy: DecoroutinatorDebugMetadataInfoResolver {
    SYSTEM_RESOURCE {
        override fun getDebugMetadataInfo(className: String): DebugMetadataInfo? {
            val path = className.replace('.', '/') + ".class"
            return loadResource(path)?.let { classBody ->
                val classReader = ClassReader(classBody)
                val classNode = ClassNode(Opcodes.ASM9)
                classReader.accept(classNode, ClassReader.SKIP_CODE)
                classNode.getDebugMetadataInfo()
            }
        }
    },

    CLASS {
        override fun getDebugMetadataInfo(className: String): DebugMetadataInfo? =
            JavaUtilsImpl.instance.getDebugMetadataInfo(className)
    },

    SYSTEM_RESOURCE_AND_CLASS {
        override fun getDebugMetadataInfo(className: String): DebugMetadataInfo? =
            SYSTEM_RESOURCE.getDebugMetadataInfo(className) ?: CLASS.getDebugMetadataInfo(className)
    }
}

interface DecoroutinatorDebugMetadataInfoResolver {
    fun getDebugMetadataInfo(className: String): DebugMetadataInfo?
}

interface DecoroutinatorJvmAgentRegistry {
    val isBaseContinuationTransformationAllowed: Boolean
        get() = System.getProperty(
            "dev.reformator.stacktracedecoroutinator.isBaseContinuationTransformationAllowed",
            "true"
        ).toBoolean()

    val isTransformationAllowed: Boolean
        get() = System.getProperty(
            "dev.reformator.stacktracedecoroutinator.isTransformationAllowed",
            "true"
        ).toBoolean()

    val isBaseContinuationRetransformationAllowed: Boolean
        get() = System.getProperty(
            "dev.reformator.stacktracedecoroutinator.isBaseContinuationRetransformationAllowed",
            "true"
        ).toBoolean()

    val isRetransformationAllowed: Boolean
        get() = System.getProperty(
            "dev.reformator.stacktracedecoroutinator.isRetransformationAllowed",
            "false"
        ).toBoolean()

    fun registerLookup(lookup: MethodHandles.Lookup) {
        err()
    }

    fun getLookup(clazz: Class<*>): MethodHandles.Lookup {
        err()
    }

    fun retransform(clazz: Class<*>) {
        err()
    }

    val metadataInfoResolver: DecoroutinatorDebugMetadataInfoResolver
        get() = System.getProperty(
            "dev.reformator.stacktracedecoroutinator.jvmAgentDebugMetadataInfoResolveStrategy",
            DecoroutinatorJvmAgentDebugMetadataInfoResolveStrategy.SYSTEM_RESOURCE_AND_CLASS.name
        ).let {
            DecoroutinatorJvmAgentDebugMetadataInfoResolveStrategy.valueOf(it)
        }

    private fun err(): Nothing {
        error(
            "Class retransformation is not allowed. " +
            "Didn't you forget to add JVM agent 'stacktrace-decoroutinator-jvm-agent' to your JVM command line."
        )
    }
}

var decoroutinatorJvmAgentRegistry: DecoroutinatorJvmAgentRegistry = object: DecoroutinatorJvmAgentRegistry { }

class DecoroutinatorJvmAgentRegistryImpl(
    private val instrumentation: Instrumentation
): DecoroutinatorJvmAgentRegistry {
    private val class2Lookup: MutableMap<Class<*>, MethodHandles.Lookup> = ConcurrentHashMap()

    override fun registerLookup(lookup: MethodHandles.Lookup) {
        class2Lookup[lookup.lookupClass()] = lookup
    }

    override fun getLookup(clazz: Class<*>): MethodHandles.Lookup =
        class2Lookup[clazz] ?: run {
            clazz.getDeclaredMethod(REGISTER_LOOKUP_METHOD_NAME).invoke(null)
            class2Lookup[clazz]!!
        }

    override val metadataInfoResolver: DecoroutinatorDebugMetadataInfoResolver = super.metadataInfoResolver

    private val _isBaseContinuationRetransformationAllowed: Boolean = super.isBaseContinuationRetransformationAllowed

    private val _isRetransformationAllowed: Boolean = super.isRetransformationAllowed

    override val isTransformationAllowed: Boolean = super.isTransformationAllowed

    override val isBaseContinuationRetransformationAllowed: Boolean
        get() = _isBaseContinuationRetransformationAllowed && instrumentation.isRetransformClassesSupported

    override val isRetransformationAllowed: Boolean
        get() = _isRetransformationAllowed && instrumentation.isRetransformClassesSupported

    override fun retransform(clazz: Class<*>) {
        instrumentation.retransformClasses(clazz)
    }

    override val isBaseContinuationTransformationAllowed: Boolean = super.isBaseContinuationTransformationAllowed
}

object DecoroutinatorJvmRegistry: BaseDecoroutinatorRegistry() {
    override val stacktraceMethodHandleRegistry: DecoroutinatorStacktraceMethodHandleRegistry
        get() = DecorountinatorJvmStacktraceMethodHandleRegistry
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
