import dev.reformator.stacktracedecoroutinator.provider.awakeBaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.isBaseContinuationPrepared
import dev.reformator.stacktracedecoroutinator.provider.isDecoroutinatorEnabled
import dev.reformator.stacktracedecoroutinator.provider.prepareBaseContinuation
import java.lang.invoke.MethodHandles

class BaseContinuation

@Suppress("unused")
fun BaseContinuation.resumeWith(result: Any?) {
    if (isDecoroutinatorEnabled) {
        if (!isBaseContinuationPrepared) {
            prepareBaseContinuation(MethodHandles.lookup())
        }
        awakeBaseContinuation(this, result)
        return
    }
    default()
}

@Suppress("UnusedReceiverParameter")
fun BaseContinuation.default() {}
