import dev.reformator.stacktracedecoroutinator.common.internal.AnnotationMetadataResolver;
import dev.reformator.stacktracedecoroutinator.generatorjvm.internal.GeneratorJvmSpecMethodsFactory;
import dev.reformator.stacktracedecoroutinator.common.internal.SpecMethodsFactory;
import dev.reformator.stacktracedecoroutinator.generatorjvm.internal.AnnotationMetadataResolverImpl;

module dev.reformator.stacktracedecoroutinator.generatorjvm {
    requires static dev.reformator.bytecodeprocessor.intrinsics;
    requires static intrinsics;

    requires kotlin.stdlib;
    requires org.objectweb.asm.tree;
    requires dev.reformator.stacktracedecoroutinator.provider;
    requires dev.reformator.stacktracedecoroutinator.common;
    requires dev.reformator.stacktracedecoroutinator.specmethodbuilder;

    exports dev.reformator.stacktracedecoroutinator.generatorjvm.internal to
            dev.reformator.stacktracedecoroutinator.jvmagentcommon,
            dev.reformator.stacktracedecoroutinator.generator.tests;

    provides SpecMethodsFactory with GeneratorJvmSpecMethodsFactory;
    provides AnnotationMetadataResolver with AnnotationMetadataResolverImpl;
}
