import dev.reformator.stacktracedecoroutinator.generator.internal.GeneratorSpecMethodsRegistry;
import dev.reformator.stacktracedecoroutinator.common.internal.SpecMethodsRegistry;

module dev.reformator.stacktracedecoroutinator.generator {
    requires static dev.reformator.bytecodeprocessor.intrinsics;
    requires static intrinsics;

    requires kotlin.stdlib;
    requires org.objectweb.asm.tree;
    requires dev.reformator.stacktracedecoroutinator.provider;
    requires dev.reformator.stacktracedecoroutinator.common;

    exports dev.reformator.stacktracedecoroutinator.generator.internal to
            dev.reformator.stacktracedecoroutinator.jvmagentcommon,
            dev.reformator.stacktracedecoroutinator.generator.tests;

    provides SpecMethodsRegistry with GeneratorSpecMethodsRegistry;
}
