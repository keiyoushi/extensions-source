package eu.kanade.tachiyomi.extension.all.e621

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat

private const val TAG_MODE_ENABLE_PREF = "tag_mode_enable"
private const val SPLIT_CHAPTERS_PREF = "split_chapters2" // Set to 2 to avoid user caching issues
private const val POPULAR_MODE_PREF = "popular_mode"
private const val CATEGORY_PREF = "category_filter"
private const val BLACKLIST_PREF = "blacklist"
private const val SCORE_THRESH_PREF = "score_tresh"
private const val FIRST_END_PREF = "first_end"

fun setupE621PreferenceScreen(screen: PreferenceScreen) {
    ListPreference(screen.context).apply {
        key = SPLIT_CHAPTERS_PREF
        title = "Split chapters by"
        entries = arrayOf("Individual posts", "Individual chapters (slower)", "Merged chapters")
        entryValues = arrayOf("posts", "chapters", "merged")
        setDefaultValue("chapters")
        summary = "%s"
    }.also(screen::addPreference)

    SwitchPreferenceCompat(screen.context).apply {
        key = TAG_MODE_ENABLE_PREF
        title = "Enable Tag Mode for Popular and Latest"
        summary = "Order Popular by score and improve results for Latest (can result in duplicate pools). When disabled, Popular is ordered by number of posts rather than popularity."
        setDefaultValue(true)
    }.also(screen::addPreference)

    SwitchPreferenceCompat(screen.context).apply {
        key = FIRST_END_PREF
        title = "Enable First Page/Last Page filter for Popular"
        summary = "Searches pools by their First and Last page. Can improve results, but will also hide posts that don't utilize those tags. (Tag Search Mode only)"
        setDefaultValue(false)
    }.also(screen::addPreference)

    ListPreference(screen.context).apply {
        key = POPULAR_MODE_PREF
        title = "Filter Popular by (Tag Mode)"
        entries = arrayOf("Hot", "Week", "Month", "Year", "All Time")
        entryValues = arrayOf("order:hot", "order:score date:week", "order:score date:month", "order:score date:year", "order:score")
        setDefaultValue("order:score date:month")
        summary = "%s"
    }.also(screen::addPreference)

    EditTextPreference(screen.context).apply {
        key = BLACKLIST_PREF
        title = "Blacklisted Tags"
        summary = "Space separated blacklisted tags. !Will not filter out everything! (Tag Search Mode only)"
        setDefaultValue("gore feces urine diaper fart")
    }.also(screen::addPreference)

    EditTextPreference(screen.context).apply {
        key = SCORE_THRESH_PREF
        title = "Score Threshold for Latest"
        summary = "Filter out posts below this threshold in the Latest category (Tag Search Mode only)"
        setDefaultValue("20")
    }.also(screen::addPreference)

    ListPreference(screen.context).apply {
        key = CATEGORY_PREF
        title = "Pool category filter for Popular and Latest (No Tag Mode)"
        entries = arrayOf("Series only", "Collections only", "Both")
        entryValues = arrayOf("series", "collection", "")
        setDefaultValue("series")
        summary = "%s"
    }.also(screen::addPreference)
}

val SharedPreferences.searchModePref: String
    get() = if (getBoolean(TAG_MODE_ENABLE_PREF, true)) "tags" else "pools"

val SharedPreferences.firstEndPref: String
    get() = if (getBoolean(FIRST_END_PREF, false)) "( ~first_page ~end_page )" else ""

val SharedPreferences.splitChaptersPref: String
    get() = getString(SPLIT_CHAPTERS_PREF, "chapters")!!

val SharedPreferences.categoryPref: String
    get() = getString(CATEGORY_PREF, "series")!!

val SharedPreferences.blacklistPref: String
    get() = getString(BLACKLIST_PREF, "gore feces urine diaper fart")!!.trim().replace(Regex("\\s+"), " ")
        .split(" ").joinToString(" ") { "-$it" }

val SharedPreferences.popularModePref: String
    get() = getString(POPULAR_MODE_PREF, "order:score date:month")!!

val SharedPreferences.scoreThreshPref: String
    get() = getString(SCORE_THRESH_PREF, "20")!!.replace(Regex("\\s+"), " ")
