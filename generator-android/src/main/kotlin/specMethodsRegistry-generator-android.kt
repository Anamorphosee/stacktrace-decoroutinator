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
import java.io.IOException
import java.lang.invoke.MethodHandles
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList

internal class AndroidSpecMethodsRegistry: BaseSpecMethodsRegistry() {
    init {
        //assert the platform
        @Suppress("NewApi")
        Class.forName(InMemoryDexClassLoader::class.java.name)
    }

    override fun generateSpecMethodFactories(
        className: String,
        classRevision: Int,
        fileName: String?,
        lineNumbersByMethod: Map<String, Set<Int>>
    ): Map<String, SpecMethodsFactory> {
        // https://github.com/Anamorphosee/stacktrace-decoroutinator/issues/30#issuecomment-2346066638
        repeat(3) {
            val loader = try {
                buildClassLoader(
                    className = className,
                    fileName = fileName,
                    lineNumbersByMethod = lineNumbersByMethod
                )
            // https://github.com/Anamorphosee/stacktrace-decoroutinator/issues/42#issuecomment-2508562491
            } catch (_: IOException) {
                return@repeat
            }
            val clazz = try {
                @Suppress("NewApi")
                loader.findClass(className)
            // https://github.com/Anamorphosee/stacktrace-decoroutinator/issues/42#issuecomment-2508562491
            } catch (_: ClassNotFoundException) {
                return@repeat
            }
            val result = run {
                lineNumbersByMethod.mapValues { (methodName, lineNumbers) ->
                    val handle = try {
                        @Suppress("NewApi")
                        MethodHandles.publicLookup().findStatic(clazz, methodName, specMethodType)
                    // https://github.com/Anamorphosee/stacktrace-decoroutinator/issues/30#issuecomment-2346066638
                    } catch (_: NoSuchMethodException) {
                        return@repeat
                    // https://github.com/Anamorphosee/stacktrace-decoroutinator/issues/39#issuecomment-2421913959
                    } catch (_: IllegalAccessException) {
                        return@repeat
                    }
                    SpecMethodsFactory { accessor, element, nextContinuation, nextSpec ->
                        assert { element.className == className }
                        assert { element.fileName == fileName }
                        assert { element.methodName == methodName }
                        assert { element.lineNumber in lineNumbers }
                        val spec = DecoroutinatorSpecImpl(
                            accessor = accessor,
                            lineNumber = element.lineNumber,
                            nextSpecAndItsMethod = nextSpec,
                            nextContinuation = nextContinuation
                        )
                        SpecAndMethodHandle(
                            specMethodHandle = handle,
                            spec = spec
                        )
                    }
                }
            }
            // https://issuetracker.google.com/issues/366474683
            loaders.add(loader)
            return result
        }
        return emptyMap()
    }

    private val loaders: MutableCollection<ClassLoader> = CopyOnWriteArrayList()
}

private fun buildClassLoader(
    className: String,
    fileName: String?,
    lineNumbersByMethod: Map<String, Set<Int>>
): InMemoryDexClassLoader {
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
    @Suppress("NewApi")
    return InMemoryDexClassLoader(
        ByteBuffer.wrap(dexFile.toDex(null, false)),
        DecoroutinatorSpec::class.java.classLoader
    )
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
