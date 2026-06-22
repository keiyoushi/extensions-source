# ProGuard Rules for ProComic Extension

# Keep Tachiyomi classes
-keep class eu.kanade.tachiyomi.** { *; }
-keep interface eu.kanade.tachiyomi.** { *; }

# Keep our classes
-keep class ar.procomic.** { *; }

# Keep Kotlin metadata
-keepclassmembers class * {
    *** Metadata(...);
}

# Keep serializable classes
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * implements kotlinx.serialization.Serializable {
    static *** Companion;
    *** serializer(...);
}

# Keep OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# Keep Jsoup
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# Keep Gson
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep Injekt
-keep class uy.kohesive.injekt.** { *; }

# Keep Android Support
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# Remove logging
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
