package keiyoushi.compiler

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName

internal val STRING = ClassName("kotlin", "String")
internal val INT = ClassName("kotlin", "Int")
internal val LONG = ClassName("kotlin", "Long")
internal val BOOLEAN = ClassName("kotlin", "Boolean")
internal val ARRAY = ClassName("kotlin", "Array")
internal val LIST = ClassName("kotlin.collections", "List")

internal val PREFERENCE_SCREEN = ClassName("androidx.preference", "PreferenceScreen")
internal val LIST_PREFERENCE = ClassName("androidx.preference", "ListPreference")
internal val EDIT_TEXT_PREFERENCE = ClassName("androidx.preference", "EditTextPreference")

internal val SHARED_PREFERENCES = ClassName("android.content", "SharedPreferences")

internal val SOURCE = ClassName("eu.kanade.tachiyomi.source", "Source")
internal val SOURCE_FACTORY = ClassName("eu.kanade.tachiyomi.source", "SourceFactory")
internal val CONFIGURABLE_SOURCE = ClassName("eu.kanade.tachiyomi.source", "ConfigurableSource")

internal val GET_PREFERENCES = MemberName("keiyoushi.utils", "getPreferences")
