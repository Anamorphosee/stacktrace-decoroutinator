module dev.reformator.stacktracedecoroutinator.testutils {
    requires kotlinx.coroutines.core;
    requires org.junit.jupiter.api;
    requires dev.reformator.bytecodeprocessor.intrinsics;
    requires junit;
    requires io.github.oshai.kotlinlogging;

    exports dev.reformator.stacktracedecoroutinator.test;

    opens dev.reformator.stacktracedecoroutinator.test;
}
