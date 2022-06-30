package dev.reformator.stacktracedecoroutinator.jvmagentcommon

import dev.reformator.stacktracedecoroutinator.common.getFileClass
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import java.lang.invoke.MethodHandles
import java.nio.file.FileSystems
import java.util.concurrent.ConcurrentHashMap

enum class DecoroutinatorJvmAgentDebugMetadataInfoResolveStrategy: DecoroutinatorDebugMetadataInfoResolver {
    SYSTEM_RESOURCE {
        override fun getDebugMetadataInfo(className: String): DebugMetadataInfo? {
            val path = className.replace('.', '/') + ".class"
            return ClassLoader.getSystemResourceAsStream(path)?.use { classBodyStream ->
                val classBody = classBodyStream.readBytes()
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
        get() = true

    val isTransformationAllowed: Boolean
        get() = true

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

    val metadataInfoResolver: DecoroutinatorDebugMetadataInfoResolver
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

    override val metadataInfoResolver: DecoroutinatorDebugMetadataInfoResolver =
        System.getProperty(
            "dev.reformator.stacktracedecoroutinator.jvmAgentDebugMetadataInfoResolveStrategy",
            DecoroutinatorJvmAgentDebugMetadataInfoResolveStrategy.SYSTEM_RESOURCE_AND_CLASS.name
        ).let {
            DecoroutinatorJvmAgentDebugMetadataInfoResolveStrategy.valueOf(it)
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
