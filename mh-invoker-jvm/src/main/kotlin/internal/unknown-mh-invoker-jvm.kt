@file:Suppress("PackageDirectoryMismatch")

package dcunknownjvm

import dev.reformator.bytecodeprocessor.intrinsics.ownerClass
import java.lang.invoke.MethodHandles

internal fun getUnknownPackageLookup(): MethodHandles.Lookup =
    MethodHandles.lookup()

internal fun getUnknownPackageName(): String =
    ownerClass.packageName
