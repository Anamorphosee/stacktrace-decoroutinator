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
import dev.reformator.stacktracedecoroutinator.common.internal.*
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import java.lang.invoke.MethodHandles
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList

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
            DecoroutinatorSpec::class.java.classLoader
        )
        // https://issuetracker.google.com/issues/366474683
        loaders.add(loader)
        val clazz = loader.findClass(className)
        return lineNumbersByMethod.mapValues { (methodName, lineNumbers) ->
            val handle = MethodHandles.publicLookup().findStatic(clazz, methodName, specMethodType)
            SpecMethodsFactory { cookie, element, nextContinuation, nextSpec ->
                assert { element.className == className }
                assert { element.fileName == fileName }
                assert { element.methodName == methodName }
                assert { element.lineNumber in lineNumbers }
                val spec = DecoroutinatorSpecImpl(
                    cookie = cookie,
                    lineNumber = element.lineNumber,
                    nextSpecAndItsMethod = nextSpec,
                    nextContinuation = nextContinuation
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

private val classLoaderFindClassMethod: Method =
    ClassLoader::class.java.getDeclaredMethod(
        ClassLoader::findClass.name,
        String::class.java
    ).also {
        it.isAccessible = true
    }

private fun ClassLoader.findClass(className: String): Class<*>? =
    classLoaderFindClassMethod.invoke(this, className) as Class<*>?
