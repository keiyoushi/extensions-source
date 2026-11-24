-keep public class keiyoushi.lib.cronetruntime.CronetRuntime { public static *; }
# Play services' Cronet module links against the vendored Cronet API
-keep class org.chromium.net.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontobfuscate
# Play services reads the wrapped value reflectively
-keepclassmembers class keiyoushi.lib.cronetruntime.PlayServicesCronet$Wrapper { <fields>; }
