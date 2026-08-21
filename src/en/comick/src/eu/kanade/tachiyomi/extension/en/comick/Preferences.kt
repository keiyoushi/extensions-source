package eu.kanade.tachiyomi.extension.en.comick

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat

object Preferences {

    const val PREF_PREFERRED_GROUPS = "preferred_groups"
    const val PREF_IGNORED_GROUPS = "ignored_groups"
    const val PREF_CONTENT_RATING = "content_rating"
    const val PREF_SHOW_ALT_TITLES = "show_alt_titles"
    const val PREF_TAG_MODE = "tag_mode"
    const val PREF_TRANSLATED_TITLE = "translated_title"
    const val PREF_HIDE_EXTRAS = "hide_extras"
    const val PREF_SORT_CHAPTERS = "sort_chapters"
    const val PREF_SHOW_SCORE = "show_score"

    fun preferredGroups(prefs: SharedPreferences): List<String> = prefs.getString(PREF_PREFERRED_GROUPS, "")
        ?.lines()
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()

    fun ignoredGroups(prefs: SharedPreferences): List<String> = prefs.getString(PREF_IGNORED_GROUPS, "")
        ?.lines()
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()

    /**
     * Inclusive content ratings for the API.
     * Hierarchy: safe ⊂ suggestive ⊂ erotica ⊂ pornographic (everything).
     * Legacy stored value "all" is treated as pornographic (no filter).
     */
    fun contentRatings(prefs: SharedPreferences): List<String>? {
        val raw = prefs.getString(PREF_CONTENT_RATING, "pornographic") ?: "pornographic"
        val v = if (raw == "all") {
            // Persist migration so ListPreference matches entryValues
            prefs.edit().putString(PREF_CONTENT_RATING, "pornographic").apply()
            "pornographic"
        } else {
            raw
        }
        return when (v) {
            "safe" -> listOf("safe")
            "suggestive" -> listOf("safe", "suggestive")
            "erotica" -> listOf("safe", "suggestive", "erotica")
            else -> null // pornographic or unknown → no filter
        }
    }

    fun showAltTitles(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_SHOW_ALT_TITLES, true)

    /** "basic" = Genre only; "full" = Genre + Theme + Format */
    fun tagMode(prefs: SharedPreferences): String = prefs.getString(PREF_TAG_MODE, "full") ?: "full"

    fun translatedTitle(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_TRANSLATED_TITLE, true)

    fun hideExtras(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_HIDE_EXTRAS, false)

    fun sortChapters(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_SORT_CHAPTERS, false)

    fun showScore(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_SHOW_SCORE, true)

    fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = PREF_PREFERRED_GROUPS
            title = "Preferred groups"
            summary = "One group name per line"
            setDefaultValue("")
            dialogTitle = "Preferred groups"
            screen.addPreference(this)
        }

        EditTextPreference(screen.context).apply {
            key = PREF_IGNORED_GROUPS
            title = "Ignored groups"
            summary = "One group name per line"
            setDefaultValue("")
            dialogTitle = "Ignored groups"
            screen.addPreference(this)
        }

        ListPreference(screen.context).apply {
            key = PREF_CONTENT_RATING
            title = "Content rating (browse)"
            summary = "%s"
            entries = arrayOf(
                "Safe",
                "Suggestive (+ Safe)",
                "Erotica (+ lower)",
                "Pornographic (all)",
            )
            entryValues = arrayOf("safe", "suggestive", "erotica", "pornographic")
            setDefaultValue("pornographic")
            screen.addPreference(this)
        }

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_TRANSLATED_TITLE
            title = "Translated title"
            summary = "Prefer English / official title"
            setDefaultValue(true)
            screen.addPreference(this)
        }

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_ALT_TITLES
            title = "Show alternative titles"
            summary = "In description"
            setDefaultValue(true)
            screen.addPreference(this)
        }

        ListPreference(screen.context).apply {
            key = PREF_TAG_MODE
            title = "Tags on series page"
            summary = "%s"
            entries = arrayOf(
                "Basic (genres only)",
                "Full (genres + themes + format + tags)",
            )
            entryValues = arrayOf("basic", "full")
            setDefaultValue("full")
            screen.addPreference(this)
        }

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_SCORE
            title = "Show score in description"
            summary = "Stars + vote count"
            setDefaultValue(true)
            screen.addPreference(this)
        }

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_EXTRAS
            title = "Hide extras / announcements"
            summary = "Hide ch. 0 / unnumbered"
            setDefaultValue(false)
            screen.addPreference(this)
        }

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SORT_CHAPTERS
            title = "Sort chapters by number"
            summary = "Ascending order; extras last"
            setDefaultValue(false)
            screen.addPreference(this)
        }
    }
}
