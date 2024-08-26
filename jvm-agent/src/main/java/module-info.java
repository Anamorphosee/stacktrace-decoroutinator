module dev.reformator.stacktracedecoroutinator.jvmagent {
    requires static kotlin.stdlib;

    exports dev.reformator.stacktracedecoroutinator.jvmagent;

    requires dev.reformator.stacktracedecoroutinator.jvmagentcommon;
    requires java.instrument;
}
