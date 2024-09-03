module dev.reformator.stacktracedecoroutinator.testutils {
    requires kotlinx.coroutines.core;
    requires org.junit.jupiter.api;
    requires junit;
    requires io.github.oshai.kotlinlogging;
    requires dev.reformator.stacktracedecoroutinator.common;
    requires static dev.reformator.bytecodeprocessor.intrinsics;

    exports dev.reformator.stacktracedecoroutinator.test;

    opens dev.reformator.stacktracedecoroutinator.test;
}
