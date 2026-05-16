package keiyoushi.gradle.extension.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName

// ---- Kotlin built-ins ----
internal val STRING = ClassName("kotlin", "String")
internal val INT = ClassName("kotlin", "Int")
internal val LONG = ClassName("kotlin", "Long")
internal val BOOLEAN = ClassName("kotlin", "Boolean")
internal val ARRAY = ClassName("kotlin", "Array")
internal val LIST = ClassName("kotlin.collections", "List")

// ---- AndroidX preference ----
internal val PREFERENCE_SCREEN = ClassName("androidx.preference", "PreferenceScreen")
internal val LIST_PREFERENCE = ClassName("androidx.preference", "ListPreference")
internal val EDIT_TEXT_PREFERENCE = ClassName("androidx.preference", "EditTextPreference")

// ---- Android ----
internal val SHARED_PREFERENCES = ClassName("android.content", "SharedPreferences")

// ---- Tachiyomi / Mihon ----
internal val SOURCE = ClassName("eu.kanade.tachiyomi.source", "Source")
internal val SOURCE_FACTORY = ClassName("eu.kanade.tachiyomi.source", "SourceFactory")
internal val CONFIGURABLE_SOURCE = ClassName("eu.kanade.tachiyomi.source", "ConfigurableSource")

// ---- Keiyoushi utils ----
internal val GET_PREFERENCES = MemberName("keiyoushi.utils", "getPreferences")
