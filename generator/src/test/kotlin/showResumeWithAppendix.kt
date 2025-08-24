import dev.reformator.stacktracedecoroutinator.provider.internal.awakeBaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.internal.baseContinuationAccessor
import dev.reformator.stacktracedecoroutinator.provider.internal.getElementFactoryStacktraceElement
import dev.reformator.stacktracedecoroutinator.provider.internal.isUsingElementFactoryForBaseContinuationEnabled
import dev.reformator.stacktracedecoroutinator.provider.internal.prepareBaseContinuationAccessor
import dev.reformator.stacktracedecoroutinator.provider.isDecoroutinatorEnabled
import org.junit.jupiter.api.Assertions.fail
import java.lang.invoke.MethodHandles

class BaseContinuation

@Suppress("unused")
fun BaseContinuation.resumeWith(result: Any?) {
    if (isDecoroutinatorEnabled) {
        awakeBaseContinuation(
            accessor = baseContinuationAccessor ?: prepareBaseContinuationAccessor(MethodHandles.lookup()),
            baseContinuation = this,
            result =result
        )
        return
    }
    default()
}

@Suppress("unused")
fun BaseContinuation.getStackTraceElement(): StackTraceElement? {
    if (isUsingElementFactoryForBaseContinuationEnabled) {
        return getElementFactoryStacktraceElement(this)
    }
    default()
}

@Suppress("UnusedReceiverParameter")
fun BaseContinuation.default(): Nothing = fail()
