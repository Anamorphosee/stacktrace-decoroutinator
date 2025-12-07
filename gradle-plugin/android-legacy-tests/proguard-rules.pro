-dontwarn javax.**
-dontwarn reactor.**
-dontwarn android.test.AndroidTestCase
-dontwarn android.test.InstrumentationTestCase
-dontwarn com.google.errorprone.annotations.**

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
