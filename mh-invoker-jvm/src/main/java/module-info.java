import dev.reformator.stacktracedecoroutinator.common.internal.MethodHandleInvoker;
import dev.reformator.stacktracedecoroutinator.common.internal.VarHandleInvoker;
import dev.reformator.stacktracedecoroutinator.mhinvokerjvm.internal.JvmMethodHandleInvoker;
import dev.reformator.stacktracedecoroutinator.mhinvokerjvm.internal.JvmVarHandleInvoker;

module dev.reformator.stacktracedecoroutinator.mhinvokerjvm {
    requires static dev.reformator.bytecodeprocessor.intrinsics;
    requires static intrinsics;

    requires dev.reformator.stacktracedecoroutinator.provider;
    requires dev.reformator.stacktracedecoroutinator.common;
    requires kotlin.stdlib;

    provides MethodHandleInvoker with JvmMethodHandleInvoker;
    provides VarHandleInvoker with JvmVarHandleInvoker;
}
