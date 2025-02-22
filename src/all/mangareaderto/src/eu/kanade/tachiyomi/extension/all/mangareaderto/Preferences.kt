package eu.kanade.tachiyomi.extension.all.mangareaderto

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.ListPreference

fun getPreferences(context: Context) = arrayOf(

    ListPreference(context).apply {
        key = QUALITY_PREF
        title = "Image quality"
        summary = "%s\n" +
            "Changes will not be applied to chapters that are already loaded or read " +
            "until you clear the chapter cache."
        entries = arrayOf("Low", "Medium", "High")
        entryValues = arrayOf("low", QUALITY_MEDIUM, "high")
        setDefaultValue(QUALITY_MEDIUM)
    },
)

val SharedPreferences.quality
    get() =
        getString(QUALITY_PREF, QUALITY_MEDIUM)!!

private const val QUALITY_PREF = "quality"
private const val QUALITY_MEDIUM = "medium"
