package eu.kanade.tachiyomi.extension.all.e621

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat

private const val CATEGORY_PREF = "category_filter"
private const val SPLIT_CHAPTERS_PREF = "split_chapters"

fun setupE621PreferenceScreen(screen: PreferenceScreen) {
    ListPreference(screen.context).apply {
        key = CATEGORY_PREF
        title = "Pool category filter for Popular and Latest"
        entries = arrayOf("Series only", "Collections only", "Both")
        entryValues = arrayOf("series", "collection", "")
        setDefaultValue("series")
        summary = "%s"
    }.also(screen::addPreference)

    SwitchPreferenceCompat(screen.context).apply {
        key = SPLIT_CHAPTERS_PREF
        title = "Split posts into individual chapters"
        summary = "Each post in a pool will be shown as a separate chapter instead of one merged chapter"
        setDefaultValue(false)
    }.also(screen::addPreference)
}

val SharedPreferences.categoryPref: String
    get() = getString(CATEGORY_PREF, "series")!!

val SharedPreferences.splitChaptersPref: Boolean
    get() = getBoolean(SPLIT_CHAPTERS_PREF, false)
