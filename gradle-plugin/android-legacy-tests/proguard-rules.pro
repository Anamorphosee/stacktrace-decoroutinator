-dontwarn javax.**
-dontwarn reactor.**

-keepclasseswithmembers class * {
    @org.junit.Test <methods>;
    <init>();
}
-keepclasseswithmembers class * {
    @org.junit.Before <methods>;
    <init>();
}
-keep class androidx.test.runner.AndroidJUnitRunner { *; }
-keep class dev.reformator.stacktracedecoroutinator.test.Test_utilsKt {
    setRetraceMappingFiles(java.lang.String[]);
}
