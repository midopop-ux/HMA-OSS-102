# libxposed API 102 interface preservation
-keep class io.github.libxposed.api.** { *; }
-keep interface io.github.libxposed.api.** { *; }
-dontwarn io.github.libxposed.**

# Keep entry point and resource references
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
    *;
}

# Keep Hooker implementations and intercept methods
-keep class ** implements io.github.libxposed.api.XposedInterface$Hooker { *; }
-keepclassmembers class ** implements io.github.libxposed.api.XposedInterface$Hooker {
    public java.lang.Object intercept(io.github.libxposed.api.XposedInterface$Chain);
}

# Keep all xposed module classes and reflection targets intact
-keep class icu.nullptr.hidemyapplist.xposed.** { *; }
-keepclassmembers class icu.nullptr.hidemyapplist.xposed.** { *; }

-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE
