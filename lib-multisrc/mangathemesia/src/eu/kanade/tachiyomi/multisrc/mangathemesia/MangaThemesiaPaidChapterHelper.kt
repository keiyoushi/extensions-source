package eu.kanade.tachiyomi.multisrc.mangathemesia
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.lib.i18n.Intl

class MangaThemesiaPaidChapterHelper(
    private val hidePaidChaptersPrefKey: String = "pref_hide_paid_chapters",
    private val lockedChapterSelector: String = "a[data-bs-target='#lockedChapterModal']",
) {
    fun addHidePaidChaptersPreferenceToScreen(screen: PreferenceScreen, intl: Intl) {
        SwitchPreferenceCompat(screen.context).apply {
            key = hidePaidChaptersPrefKey
            title = intl["pref_hide_paid_chapters_title"]
            summary = intl["pref_hide_paid_chapters_summary"]
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    fun getHidePaidChaptersPref(preferences: SharedPreferences) = preferences.getBoolean(hidePaidChaptersPrefKey, true)

    fun getChapterListSelectorBasedOnHidePaidChaptersPref(baseChapterListSelector: String, preferences: SharedPreferences): String {
        if (!getHidePaidChaptersPref(preferences)) {
            return baseChapterListSelector
        }

        // Fragile
        val selectors = baseChapterListSelector.split(", ")

        return selectors
            .map { "$it:not($lockedChapterSelector):not(:has($lockedChapterSelector))" }
            .joinToString()
    }
}
