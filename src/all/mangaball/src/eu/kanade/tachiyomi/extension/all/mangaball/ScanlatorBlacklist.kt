package eu.kanade.tachiyomi.extension.all.mangaball

import android.content.SharedPreferences
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.model.SChapter

// The selectable names are collected from the chapter lists the user opens, because the site
// exposes no public endpoint for the internal groups that upload its chapters.

internal fun SharedPreferences.scanlatorBlacklist(): Set<String> = getStringSet(SCANLATOR_BLACKLIST_PREF, emptySet())
    .orEmpty()
    .mapTo(mutableSetOf()) { it.trim().lowercase() }

internal fun SharedPreferences.knownScanlatorNames(): List<String> = getStringSet(KNOWN_SCANLATORS_PREF, emptySet())
    .orEmpty()
    .sortedBy { it.lowercase() }

internal fun SharedPreferences.rememberScanlators(names: List<String>) {
    val known = getStringSet(KNOWN_SCANLATORS_PREF, emptySet()).orEmpty()
    val new = names.filter { it.isNotBlank() }.toSet() - known
    if (new.isEmpty()) return
    edit().putStringSet(KNOWN_SCANLATORS_PREF, known + new).apply()
}

internal fun List<SChapter>.filterBlacklistedScanlators(blacklist: Set<String>): List<SChapter> = filterNot { it.scanlator?.trim()?.lowercase()?.let(blacklist::contains) == true }

internal fun PreferenceScreen.addScanlatorBlacklistPreference(preferences: SharedPreferences) {
    val scanlators = preferences.knownScanlatorNames().toTypedArray()
    MultiSelectListPreference(context).apply {
        key = SCANLATOR_BLACKLIST_PREF
        title = "Scanlator blacklist"
        summary = "Hide chapters from the selected scanlators. The list fills up automatically as you browse titles."
        entries = scanlators
        entryValues = scanlators
        setDefaultValue(emptySet<String>())
    }.also(::addPreference)
}

private const val SCANLATOR_BLACKLIST_PREF = "scanlator_blacklist_pref"
private const val KNOWN_SCANLATORS_PREF = "known_scanlators_pref"
