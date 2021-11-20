package dev.reformator.stacktracedecoroutinator.generator

import dev.reformator.stacktracedecoroutinator.analyzer.DecoroutinatorClassSpec
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.function.Function

class DecoroutinatorClassLoader(
    parent: ClassLoader = getSystemClassLoader(),
    private val generator: DecoroutinatorClassBodyGenerator = DecoroutinatorClassBodyGeneratorImpl()
): ClassLoader(parent) {
    companion object {
        val lookup: MethodHandles.Lookup = MethodHandles.publicLookup()
        val invokeStacktraceMethodType = MethodType.methodType(
            Object::class.java, // return type
            Array<MethodHandle>::class.java,
            IntArray::class.java,
            Int::class.java,
            Function::class.java,
            Object::class.java,
            Object::class.java
        )
    }


    fun getMethodName2StacktraceHandlersMap(
        className: String,
        classSpec: DecoroutinatorClassSpec
    ): Map<String, MethodHandle> {
        val clazz: Class<*> = findLoadedClass(className) ?: run {
            val classBody = generator(className, classSpec)
            defineClass(className, classBody, 0, classBody.size)
        }
        return classSpec.continuationClassName2Method.values.asSequence()
            .distinct()
            .map {
                it.methodName to lookup.findStatic(clazz, it.methodName, invokeStacktraceMethodType)
            }
            .toMap()
    }
}
