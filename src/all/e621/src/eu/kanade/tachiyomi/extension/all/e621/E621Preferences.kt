package eu.kanade.tachiyomi.extension.all.e621

import android.content.SharedPreferences
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat

private const val CATEGORY_PREF = "category_filter"
private const val SPLIT_CHAPTERS_PREF = "split_chapters"
private const val USERNAME_PREF = "username"
private const val API_KEY_PREF = "api_key"
private const val ACCOUNT_BLACKLIST_PREF = "account_blacklist"

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

    EditTextPreference(screen.context).apply {
        key = USERNAME_PREF
        title = "Username"
        summary = "Optional: e621 account username for authenticated requests"
        setDefaultValue("")
    }.also(screen::addPreference)

    EditTextPreference(screen.context).apply {
        key = API_KEY_PREF
        title = "API key"
        summary = "Optional: e621 account API key"
        setDefaultValue("")
        setOnBindEditTextListener {
            it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
    }.also(screen::addPreference)

    SwitchPreferenceCompat(screen.context).apply {
        key = ACCOUNT_BLACKLIST_PREF
        title = "Apply account blacklist to posts"
        summary = "Enables blacklisting posts when loading"
        setDefaultValue(false)
    }.also(screen::addPreference)
}

val SharedPreferences.categoryPref: String
    get() = getString(CATEGORY_PREF, "series")!!

val SharedPreferences.splitChaptersPref: Boolean
    get() = getBoolean(SPLIT_CHAPTERS_PREF, false)

val SharedPreferences.usernamePref: String
    get() = getString(USERNAME_PREF, "")!!

val SharedPreferences.apiKeyPref: String
    get() = getString(API_KEY_PREF, "")!!

val SharedPreferences.accountBlacklistPref: Boolean
    get() = getBoolean(ACCOUNT_BLACKLIST_PREF, false)
