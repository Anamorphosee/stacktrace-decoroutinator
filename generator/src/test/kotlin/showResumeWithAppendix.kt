import dev.reformator.stacktracedecoroutinator.provider.awakeBaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.cookie
import dev.reformator.stacktracedecoroutinator.provider.isDecoroutinatorEnabled
import dev.reformator.stacktracedecoroutinator.provider.prepareCookie
import java.lang.invoke.MethodHandles

class BaseContinuation

@Suppress("unused")
fun BaseContinuation.resumeWith(result: Any?) {
    if (isDecoroutinatorEnabled) {
        awakeBaseContinuation(cookie ?: prepareCookie(MethodHandles.lookup()), this, result)
        return
    }
    default()
}

@Suppress("UnusedReceiverParameter")
fun BaseContinuation.default() {}
