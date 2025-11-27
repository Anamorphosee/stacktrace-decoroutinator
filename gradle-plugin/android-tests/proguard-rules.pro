-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn java.**
-dontwarn javax.**
-dontwarn reactor.**

-keepclasseswithmembers class * {
    @org.junit.Test <methods>;
}
-keep class androidx.test.runner.AndroidJUnitRunner { *; }
-keepclassmembers class * {
    <init>();
}
