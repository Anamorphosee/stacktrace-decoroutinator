package dev.reformator.stacktracedecoroutinator.common

import dev.reformator.stacktracedecoroutinator.generator.DecoroutinatorClassLoader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import java.lang.invoke.MethodHandle
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.BiFunction


object DecoroutinatorStacktraceMethodHandleRegistryImpl: BaseDecoroutinatorStacktraceMethodHandleRegistry() {
    private const val STACK_METHOD_HANDLERS_VAR_INDEX = 0
    private const val LINE_NUMBERS_VAR_INDEX = 1
    private const val NEXT_STEP_VAR_INDEX = 2
    private const val INVOKE_FUNCTION_VAR_INDEX = 3
    private const val RESULT_VAR_INDEX = 4
    private const val SUSPEND_OBJECT_VAR_INDEX = 5
    private const val LINE_NUMBER_VAR_INDEX = 6

    private val METHOD_HANDLE_INTERNAL_CLASS_NAME = Type.getInternalName(MethodHandle::class.java)
    private val BI_FUNCTION_INTERNAL_CLASS_NAME = Type.getInternalName(BiFunction::class.java)
    private val OBJECT_INTERNAL_CLASS_NAME = Type.getInternalName(Object::class.java)
    private val INTEGER_INTERNAL_CLASS_NAME = Type.getInternalName(Integer::class.java)
    private val STRING_BUILDER_INTERNAL_CLASS_NAME = Type.getInternalName(java.lang.StringBuilder::class.java)
    private val STRING_INTERNAL_CLASS_NAME = Type.getInternalName(java.lang.String::class.java)

    //MethodHandle[] stackMethodHandlers
    //int[] stackLineNumbers
    //int nextStep
    //BiFunction<Integer, Object, Object> continuationInvokeMethods
    //Object result
    //Object suspendObject
    private val METHOD_DESC = "(" +
            "[L$METHOD_HANDLE_INTERNAL_CLASS_NAME;" +
            "[I" +
            "I" +
            "L$BI_FUNCTION_INTERNAL_CLASS_NAME;" +
            "L$OBJECT_INTERNAL_CLASS_NAME;" +
            "L$OBJECT_INTERNAL_CLASS_NAME;" +
            ")L$OBJECT_INTERNAL_CLASS_NAME;"

    private val classLoaderByRevision: MutableList<DecoroutinatorClassLoader> = CopyOnWriteArrayList()

    override fun generateStacktraceClass(
        className: String,
        fileName: String?,
        classRevision: Int,
        methodName2LineNumbers: Map<String, Set<Int>>
    ): Class<*> {
        val classBody = getClassBody(className, fileName, methodName2LineNumbers)

        while (classLoaderByRevision.size <= classRevision) {
            classLoaderByRevision.add(DecoroutinatorClassLoader())
        }

        return classLoaderByRevision[classRevision].defineClass(className, classBody)
    }

    private fun getClassBody(
        className: String,
        fileName: String?,
        methodName2LineNumbers: Map<String, Set<Int>>
    ): ByteArray {
        val classNode = ClassNode(Opcodes.ASM9).apply {
            version = Opcodes.V1_8
            access = Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER
            name = className.replace('.', '/')
            superName = OBJECT_INTERNAL_CLASS_NAME
            sourceFile = fileName
        }
        classNode.methods = methodName2LineNumbers.entries.map { (methodName, lineNumbers) ->


            methodNode
        }

        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        classNode.accept(writer)
        return writer.toByteArray()
    }


}
