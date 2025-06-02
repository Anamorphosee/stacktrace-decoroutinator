module dev.reformator.stacktracedecoroutinator.gradleplugintests {
    requires dev.reformator.stacktracedecoroutinator.testutils;
    //noinspection JavaModuleDefinition
    requires kotlinx.coroutines.core;
    requires kotlin.test.junit5;
    requires dev.reformator.stacktracedecoroutinator.testutilsjvm;
    requires dev.reformator.bytecodeprocessor.intrinsics;
    requires dev.reformator.stacktracedecoroutinator.duplicatejar;
    requires org.jetbrains.annotations;

    exports dev.reformator.stacktracedecoroutinator.gradleplugintests;
}
