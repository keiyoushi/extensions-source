-keep public class keiyoushi.lib.cronetruntime.CronetRuntime { public static *; }
# Play services' Cronet module links against the vendored Cronet API
-keep class org.chromium.net.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn kotlin.**
-dontwarn androidx.**
-dontobfuscate
