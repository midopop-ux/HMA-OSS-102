# Enum class
-keepclassmembers,allowoptimization enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class icu.nullptr.hidemyapplist.data.UpdateData { *; }
-keep class icu.nullptr.hidemyapplist.data.UpdateData$* { *; }

-keep,allowoptimization class * extends androidx.preference.PreferenceFragmentCompat
-keepclassmembers class org.frknkrc44.hma_oss.databinding.**  {
    public <methods>;
}

# Modern libxposed API 102
-dontwarn io.github.libxposed.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
-keep class icu.nullptr.hidemyapplist.xposed.XposedEntry { *; }
