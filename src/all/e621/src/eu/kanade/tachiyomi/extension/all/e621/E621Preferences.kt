package eu.kanade.tachiyomi.extension.all.e621

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat

private const val TAG_MODE_ENABLE_PREF = "tag_mode_enable"
private const val SPLIT_CHAPTERS_PREF = "split_chapters2" // Set to 2 to avoid user caching issues
private const val CATEGORY_PREF = "category_filter"
private const val BLACKLIST_PREF = "blacklist"

fun setupE621PreferenceScreen(screen: PreferenceScreen) {
    SwitchPreferenceCompat(screen.context).apply {
        key = TAG_MODE_ENABLE_PREF
        title = "Enable Tag Mode for Popular"
        summary = "Order pools by score in Popular (can result in duplicate pools). When disabled, Popular is ordered by number of posts instead."
        setDefaultValue(true)
    }.also(screen::addPreference)

    ListPreference(screen.context).apply {
        key = SPLIT_CHAPTERS_PREF
        title = "Split chapters by..."
        entries = arrayOf("Individual posts", "Individual chapters", "Merged chapters")
        entryValues = arrayOf("posts", "chapters", "merged")
        setDefaultValue("chapters")
        summary = "%s"
    }.also(screen::addPreference)

    ListPreference(screen.context).apply {
        key = CATEGORY_PREF
        title = "Pool category filter for Popular and Latest (Requires Tag Mode Disabled)"
        entries = arrayOf("Series only", "Collections only", "Both")
        entryValues = arrayOf("series", "collection", "")
        setDefaultValue("series")
        summary = "%s"
    }.also(screen::addPreference)

    // I couldn't figure out how to get this to work.
    // EditTextPreference(screen.context).apply {
    //     key = BLACKLIST_PREF
    //     title = "Blacklisted Tags (space separated. Will not filter everything)"
    //     // summary = "Space separated blacklisted tags. Will not filter out everything unless searching for First Page (Tag Search Mode only)"
    //     summary = "%s"
    //     setDefaultValue("")
    // }.also(screen::addPreference)
}

val SharedPreferences.searchModePref: String
    get() = if (getBoolean(TAG_MODE_ENABLE_PREF, false)) "tags" else "pools"

val SharedPreferences.splitChaptersPref: String
    get() = getString(SPLIT_CHAPTERS_PREF, "chapters")!!

val SharedPreferences.categoryPref: String
    get() = getString(CATEGORY_PREF, "series")!!
