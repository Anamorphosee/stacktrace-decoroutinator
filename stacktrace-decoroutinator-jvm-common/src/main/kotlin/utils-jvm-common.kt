package dev.reformator.stacktracedecoroutinator.jvmcommon

import dev.reformator.stacktracedecoroutinator.common.DecoroutinatorMarker
import org.objectweb.asm.Type

private val decoroutinatorMarkerClassDescriptor = Type.getDescriptor(DecoroutinatorMarker::class.java)

fun loadDecoroutinatorBaseContinuationClassBody(): ByteArray =
    ClassLoader.getSystemResourceAsStream("decoroutinatorBaseContinuation.class").use {
        it.readBytes()
    }
