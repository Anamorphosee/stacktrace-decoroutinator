module dev.reformator.stacktracedecoroutinator.classtransformer {
    requires static dev.reformator.bytecodeprocessor.intrinsics;

    requires kotlin.stdlib;
    requires dev.reformator.stacktracedecoroutinator.provider;
    requires dev.reformator.stacktracedecoroutinator.specmethodbuilder;
    requires org.objectweb.asm.tree;

    exports dev.reformator.stacktracedecoroutinator.classtransformer.internal to
            dev.reformator.stacktracedecoroutinator.jvmagentcommon;
}
