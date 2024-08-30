@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("SpecMethodsRegistryGeneratorAndroidKt")

package dev.reformator.stacktracedecoroutinator.generatorandroid

import com.android.dex.DexFormat
import com.android.dx.dex.DexOptions
import com.android.dx.dex.file.ClassDefItem
import com.android.dx.dex.file.DexFile
import com.android.dx.rop.cst.CstString
import com.android.dx.rop.cst.CstType
import com.android.dx.rop.type.StdTypeList
import com.android.dx.rop.type.Type
import dalvik.system.InMemoryDexClassLoader
import dev.reformator.bytecodeprocessor.intrinsics.LoadConstant
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.common.internal.BaseSpecMethodsRegistry
import dev.reformator.stacktracedecoroutinator.common.internal.SpecAndItsMethodHandle
import dev.reformator.stacktracedecoroutinator.common.internal.SpecMethodsFactory
import dev.reformator.stacktracedecoroutinator.common.internal.publicCallInvokeSuspend
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Constructor
import java.lang.reflect.Modifier
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Function
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal class AndroidSpecMethodsRegistry: BaseSpecMethodsRegistry() {
    override fun generateSpecMethodFactories(
        className: String,
        classRevision: Int,
        fileName: String?,
        lineNumbersByMethod: Map<String, Set<Int>>
    ): Map<String, SpecMethodsFactory> {
        val fileNameCstString = fileName?.let { CstString(it) }
        val dexOptions = DexOptions()
        dexOptions.minSdkVersion = DexFormat.API_METHOD_HANDLES
        val dexFile = DexFile(dexOptions)
        val clazzType = CstType(Type.internClassName(className.internalName))
        val classDef = ClassDefItem(
            clazzType,
            Modifier.PUBLIC or Modifier.FINAL,
            CstType.OBJECT,
            StdTypeList.EMPTY,
            fileNameCstString
        )
        lineNumbersByMethod.forEach { (methodName, lineNumbers) ->
            classDef.addDirectMethod(buildSpecMethod(
                dexOptions = dexOptions,
                clazz = clazzType,
                fileName = fileNameCstString,
                methodName = methodName,
                lineNumbers = lineNumbers
            ))
        }
        dexFile.add(classDef)
        val loader = InMemoryDexClassLoader(
            ByteBuffer.wrap(dexFile.toDex(null, false)),
            specClassLoader
        )
        loaders.add(loader)
        val clazz = loader.loadClass(className)
        return lineNumbersByMethod.mapValues { (methodName, lineNumbers) ->
            val handle = MethodHandles.publicLookup().findStatic(clazz, methodName, specMethodType)
            SpecMethodsFactory { cookie, element, nextContinuation, nextSpec ->
                dev.reformator.stacktracedecoroutinator.common.internal.assert { element.className == className }
                dev.reformator.stacktracedecoroutinator.common.internal.assert { element.fileName == fileName }
                dev.reformator.stacktracedecoroutinator.common.internal.assert { element.methodName == methodName }
                dev.reformator.stacktracedecoroutinator.common.internal.assert { element.lineNumber in lineNumbers }
                val spec = specConstructor.newInstance(
                    element.lineNumber,
                    nextSpec?.specMethodHandle,
                    nextSpec?.spec,
                    COROUTINE_SUSPENDED,
                    Function { result: Any? -> nextContinuation.publicCallInvokeSuspend(cookie, result) }
                )
                SpecAndItsMethodHandle(
                    specMethodHandle = handle,
                    spec = spec
                )
            }
        }
    }

    private val loaders: MutableCollection<ClassLoader> = CopyOnWriteArrayList()
}

private val isolatedSpecClassDexBodyBase64: String
    @LoadConstant get() { fail() }

@OptIn(ExperimentalEncodingApi::class)
private val specClassLoader = InMemoryDexClassLoader(
    ByteBuffer.wrap(Base64.Default.decode(isolatedSpecClassDexBodyBase64)),
    null
)

private val specClass = specClassLoader.loadClass(isolatedSpecClassName)
private val specMethodType: MethodType = MethodType.methodType(Any::class.java, specClass, Any::class.java)
private val specConstructor: Constructor<*> = specClass.getDeclaredConstructor(
    Int::class.javaPrimitiveType, // lineNumber
    MethodHandle::class.java, // nextSpecHandle
    Any::class.java, // nextSpec
    Any::class.java, // COROUTINE_SUSPENDED
    Function::class.java // resumeNext implementation
)
