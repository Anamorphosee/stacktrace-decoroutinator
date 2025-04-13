package dev.reformator.stacktracedecoroutinator.duplicatejar

import dev.reformator.bytecodeprocessor.intrinsics.GetOwnerClass
import dev.reformator.bytecodeprocessor.intrinsics.currentFileName
import dev.reformator.bytecodeprocessor.intrinsics.currentLineNumber
import dev.reformator.bytecodeprocessor.intrinsics.fail

suspend fun call(callIt: suspend () -> Any?): Any? {
    duplicateJarCallLineNumber = currentLineNumber + 1
    return callIt()
}

private val currentFileClass: Class<*>
    @GetOwnerClass(deleteAfterModification = true) get() { fail() }

val duplicateJarCallClassName = currentFileClass.name
val duplicateJarCallMethodName = ::call.name
var duplicateJarCallLineNumber = 0
val duplicateJarFileName = currentFileName
