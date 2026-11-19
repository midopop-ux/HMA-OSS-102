# Enum class
-keepclassmembers,allowoptimization enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class icu.nullptr.hidemyapplist.data.** { *; }

-keep,allowoptimization class * extends androidx.preference.PreferenceFragmentCompat
-keepclassmembers class org.frknkrc44.hma_oss.databinding.**  {
    public <methods>;
}

# Modern libxposed API 102
-keep class io.github.libxposed.api.** { *; }
-keep interface io.github.libxposed.api.** { *; }
-dontwarn io.github.libxposed.**

-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
    *;
}
-keep class icu.nullptr.hidemyapplist.xposed.XposedEntry { *; }

# Keep Hooker implementations and intercept methods
-keep class ** implements io.github.libxposed.api.XposedInterface$Hooker { *; }
-keepclassmembers class ** implements io.github.libxposed.api.XposedInterface$Hooker {
    public java.lang.Object intercept(io.github.libxposed.api.XposedInterface$Chain);
}

# Keep all xposed module classes and reflection targets intact
-keep class icu.nullptr.hidemyapplist.xposed.** { *; }
-keepclassmembers class icu.nullptr.hidemyapplist.xposed.** { *; }

# Keep all common classes (AIDL, serialization, constants)
-keep class icu.nullptr.hidemyapplist.common.** { *; }
-keepclassmembers class icu.nullptr.hidemyapplist.common.** { *; }

# BouncyCastle / Conscrypt / OpenJSSE warnings
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
