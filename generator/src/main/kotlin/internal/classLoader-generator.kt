@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.generator.internal

import dev.reformator.bytecodeprocessor.intrinsics.MakeStatic
import dev.reformator.stacktracedecoroutinator.common.internal.Cookie
import dev.reformator.stacktracedecoroutinator.common.internal.SpecAndItsMethodHandle
import dev.reformator.stacktracedecoroutinator.common.internal.publicCallInvokeSuspend
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Constructor
import java.util.function.Function
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

internal class DecoroutinatorClassLoader: ClassLoader(null) {
    fun buildClassAndGetSpecHandlesByMethod(
        className: String,
        fileName: String?,
        lineNumbersByMethod: Map<String, Set<Int>>
    ): Map<String, MethodHandle> {
        val body = getClassBody(
            className = className,
            fileName = fileName,
            lineNumbersByMethod = lineNumbersByMethod
        )
        val clazz = defineClass(className, body)
        return lineNumbersByMethod.mapValues { (methodName, _) ->
            MethodHandles.publicLookup().findStatic(clazz, methodName, specMethodType)
        }
    }

    fun getSpec(
        cookie: Cookie,
        lineNumber: Int,
        nextContinuation: BaseContinuation,
        nextSpec: SpecAndItsMethodHandle?
    ): Any =
        specImplConstructor.newInstance(
            lineNumber,
            nextSpec?.specMethodHandle,
            nextSpec?.spec,
            COROUTINE_SUSPENDED,
            Function { result: Any? -> nextContinuation.publicCallInvokeSuspend(cookie, result) }
        )

    private val specClass = defineClass(DecoroutinatorSpec::class.java)
    private val specImplClass = defineClass(GeneratorSpecImpl::class.java)
    private val specMethodType: MethodType = MethodType.methodType(Any::class.java, specClass, Any::class.java)
    private val specImplConstructor: Constructor<*> = specImplClass.getDeclaredConstructor(
        Int::class.javaPrimitiveType, // lineNumber
        MethodHandle::class.java, // nextSpecHandle
        Any::class.java, // nextSpec
        Any::class.java, // COROUTINE_SUSPENDED
        Function::class.java // resumeNext implementation
    )

    @Suppress("unused")
    @MakeStatic(addToStaticInitializer = true)
    private fun clinit() {
        assert(registerAsParallelCapable())
    }

    private fun defineClass(className: String, classBody: ByteArray): Class<*> {
        synchronized(getClassLoadingLock(className)) {
            return defineClass(className, classBody, 0, classBody.size)
        }
    }

    private fun defineClass(clazz: Class<*>): Class<*> {
        val body = loadResource(clazz.name.replace('.', '/') + ".class")!!
        return defineClass(clazz.name, body)
    }
}

private fun getClassBody(
    className: String,
    fileName: String?,
    lineNumbersByMethod: Map<String, Set<Int>>
): ByteArray {
    val classNode = ClassNode(Opcodes.ASM9).apply {
        version = Opcodes.V1_8
        access = Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER
        name = className.replace('.', '/')
        superName = Type.getInternalName(Any::class.java)
        sourceFile = fileName
        methods = lineNumbersByMethod.asSequence()
            .map { (methodName, lineNumbers) ->
                buildSpecMethodNode(
                    methodName = methodName,
                    lineNumbers = lineNumbers,
                    makePrivate = false
                )
            }
            .toList()
    }
    val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
    classNode.accept(writer)
    return writer.toByteArray()
}

class GeneratorSpecImpl(
    override val lineNumber: Int,
    private val _nextHandle: MethodHandle?,
    private val _nextSpec: Any?,
    override val coroutineSuspendedMarker: Any,
    private val resumeNext: Function<Any?, Any?>
) : DecoroutinatorSpec {
    override val nextHandle: MethodHandle
        get() = _nextHandle!!

    override val nextSpec: Any
        get() = _nextSpec!!

    override val isLastSpec: Boolean
        get() = _nextHandle == null

    override fun resumeNext(result: Any?): Any? =
        resumeNext.apply(result)
}
