import dev.reformator.stacktracedecoroutinator.common.internal.MethodHandleInvoker;
import dev.reformator.stacktracedecoroutinator.mhinvoker.internal.RegularMethodHandleInvoker;

module dev.reformator.stacktracedecoroutinator.mhinvoker {
    requires static dev.reformator.bytecodeprocessor.intrinsics;
    requires static intrinsics;

    requires dev.reformator.stacktracedecoroutinator.provider;
    requires dev.reformator.stacktracedecoroutinator.common;
    requires kotlin.stdlib;

    provides MethodHandleInvoker with RegularMethodHandleInvoker;
}
