import dev.reformator.stacktracedecoroutinator.common.internal.AnnotationMetadataResolver;
import dev.reformator.stacktracedecoroutinator.generator.internal.GeneratorSpecMethodsFactory;
import dev.reformator.stacktracedecoroutinator.common.internal.SpecMethodsFactory;
import dev.reformator.stacktracedecoroutinator.generator.internal.AnnotationMetadataResolverImpl;

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

    provides SpecMethodsFactory with GeneratorSpecMethodsFactory;
    provides AnnotationMetadataResolver with AnnotationMetadataResolverImpl;
}
