module dev.reformator.stacktracedecoroutinator.jvm.tests {
    requires dev.reformator.stacktracedecoroutinator.jvm;
    requires kotlin.test.junit5;
    requires dev.reformator.stacktracedecoroutinator.common;
    requires dev.reformator.stacktracedecoroutinator.provider;
    requires dev.reformator.stacktracedecoroutinator.testutils;
    requires dev.reformator.stacktracedecoroutinator.testutilsjvm;
    requires kotlinx.coroutines.core;

    exports dev.reformator.stacktracedecoroutinator.jvmtests;
}
