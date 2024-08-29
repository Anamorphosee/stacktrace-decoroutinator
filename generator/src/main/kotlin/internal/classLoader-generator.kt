@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("ClassLoaderGeneratorKt")

package dev.reformator.stacktracedecoroutinator.generator.internal

import dev.reformator.bytecodeprocessor.intrinsics.LoadConstant
import dev.reformator.bytecodeprocessor.intrinsics.MakeStatic
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.common.internal.Cookie
import dev.reformator.stacktracedecoroutinator.common.internal.SpecAndItsMethodHandle
import dev.reformator.stacktracedecoroutinator.common.internal.publicCallInvokeSuspend
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
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
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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

    private val specClass = defineClass(isolatedSpecClassName, isolatedSpecClassBody)
    private val specMethodType: MethodType = MethodType.methodType(Any::class.java, specClass, Any::class.java)
    private val specImplConstructor: Constructor<*> = specClass.getDeclaredConstructor(
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
}

@OptIn(ExperimentalEncodingApi::class)
private val isolatedSpecClassBody = Base64.Default.decode(isolatedSpecClassBodyBase64)

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
                    makePrivate = false,
                    specClassName = isolatedSpecClassName,
                    isSpecInterface = false
                )
            }
            .toList()
    }
    val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
    classNode.accept(writer)
    return writer.toByteArray()
}

private val isolatedSpecClassName: String
    @LoadConstant get() { fail() }

private val isolatedSpecClassBodyBase64: String
    @LoadConstant get() { fail() }
