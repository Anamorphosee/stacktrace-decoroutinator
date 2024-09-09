module dev.reformator.stacktracedecoroutinator.testutilsjvm {
    requires kotlinx.coroutines.core;
    requires org.junit.jupiter.api;
    requires io.github.oshai.kotlinlogging;
    requires static dev.reformator.bytecodeprocessor.intrinsics;

    exports dev.reformator.stacktracedecoroutinator.testjvm;
    opens dev.reformator.stacktracedecoroutinator.testjvm;
}
