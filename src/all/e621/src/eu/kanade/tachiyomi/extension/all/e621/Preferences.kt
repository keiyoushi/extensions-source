package eu.kanade.tachiyomi.extension.all.e621

import android.content.SharedPreferences
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat

private const val BETTER_DETAILS_PREF = "better_details"
private const val TAG_MODE_ENABLE_PREF = "tag_mode_enable"
private const val SPLIT_CHAPTERS_PREF = "split_chapters2"
private const val POPULAR_MODE_PREF = "popular_mode"
private const val CATEGORY_PREF = "category_filter"
private const val BLOCKED_TAGS_PREF = "blacklist"
private const val REQUIRED_TAGS_PREF = "whitelist"
private const val SCORE_THRESH_PREF = "score_thresh"
private const val FULL_RESOLUTION_PREF = "first_end"
private const val USERNAME_PREF = "username"
private const val API_KEY_PREF = "api_key"
private const val ACCOUNT_BLOCKED_TAGS_PREF = "account_blacklist"

private const val DEFAULT_BLOCKED_TAGS = "( gore necrophilia ) ( gore sex ) mutilation snuff torture gore_focus character_prepared_as_food castration feces urine diaper fart burp_cloud"

private val TAG_TOKEN_REGEX = Regex("""\([^)]*\)|\S+""")
private val WHITESPACE_REGEX = Regex("""\s+""")

fun setupE621PreferenceScreen(screen: PreferenceScreen) {
    SwitchPreferenceCompat(screen.context).apply {
        key = FULL_RESOLUTION_PREF
        title = "Enable Full Image Resolutions"
        summary = "Loads images at their full resolution. Full images often have absurd resolutions. Disabling this will speed up image loading at the tradeoff of image quality (usually not noticeable)."
        setDefaultValue(false)
    }.also(screen::addPreference)

    SwitchPreferenceCompat(screen.context).apply {
        key = BETTER_DETAILS_PREF
        title = "Enable Better Manga Details"
        summary = "Improves Manga Details by adding authors, tags, and chapter detection. Disabling this will load manga details and chapter lists faster, and reduce API calls."
        setDefaultValue(false)
    }.also(screen::addPreference)

    ListPreference(screen.context).apply {
        key = SPLIT_CHAPTERS_PREF
        title = "Split chapters by"
        entries = arrayOf("Individual posts", "Individual chapters (slower)", "Merged chapters")
        entryValues = arrayOf("posts", "chapters", "merged")
        setDefaultValue("chapters")
        summary = "%s"
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
        key = ACCOUNT_BLOCKED_TAGS_PREF
        title = "Hide posts with your account's blocked tags"
        summary = "Shows a placeholder for pages with tags blocked in your e621 account"
        setDefaultValue(false)
    }.also(screen::addPreference)

    SwitchPreferenceCompat(screen.context).apply {
        key = TAG_MODE_ENABLE_PREF
        title = "Enable Tag Mode for Popular and Latest"
        summary = "Order Popular by score and improve results for Latest (can result in duplicate pools). When disabled, Popular is ordered by number of posts rather than popularity."
        setDefaultValue(true)
    }.also(screen::addPreference)

    ListPreference(screen.context).apply {
        key = POPULAR_MODE_PREF
        title = "Filter Popular by (Tag Mode)"
        entries = arrayOf("Hot", "Week", "Month", "Year", "All Time")
        entryValues = arrayOf("order:hot", "order:score date:week", "order:score date:month", "order:score date:year", "order:score")
        setDefaultValue("order:score date:year")
        summary = "%s"
    }.also(screen::addPreference)

    EditTextPreference(screen.context).apply {
        key = BLOCKED_TAGS_PREF
        title = "Blocked tags"
        summary = "Space separated tags to leave out of searches. Not every match gets filtered (Tag Search Mode only)"
        setDefaultValue(DEFAULT_BLOCKED_TAGS)
    }.also(screen::addPreference)

    EditTextPreference(screen.context).apply {
        key = REQUIRED_TAGS_PREF
        title = "Required tags"
        summary = "Space separated tags every search result must match (Tag Search Mode only)"
        setDefaultValue("score:>10")
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

val SharedPreferences.betterDetailsPref: Boolean
    get() = getBoolean(BETTER_DETAILS_PREF, false)

val SharedPreferences.fullResolution: Boolean
    get() = getBoolean(FULL_RESOLUTION_PREF, false)

val SharedPreferences.searchModePref: String
    get() = if (getBoolean(TAG_MODE_ENABLE_PREF, true)) "tags" else "pools"

val SharedPreferences.splitChaptersPref: String
    get() = getString(SPLIT_CHAPTERS_PREF, "chapters") ?: "chapters"

val SharedPreferences.categoryPref: String
    get() = getString(CATEGORY_PREF, "series") ?: "series"

val SharedPreferences.blockedTagsPref: String
    get() = TAG_TOKEN_REGEX.findAll(
        getString(BLOCKED_TAGS_PREF, DEFAULT_BLOCKED_TAGS)?.trim()?.replace(WHITESPACE_REGEX, " ") ?: DEFAULT_BLOCKED_TAGS,
    ).joinToString(" ") { "-${it.value}" }

val SharedPreferences.requiredTagsPref: String
    get() = getString(REQUIRED_TAGS_PREF, "score:>10")?.trim()?.replace(WHITESPACE_REGEX, " ") ?: "score:>10"

val SharedPreferences.popularModePref: String
    get() = getString(POPULAR_MODE_PREF, "order:score date:year") ?: "order:score date:year"

val SharedPreferences.scoreThreshPref: String
    get() = getString(SCORE_THRESH_PREF, "20")?.replace(WHITESPACE_REGEX, " ") ?: "20"

val SharedPreferences.usernamePref: String
    get() = getString(USERNAME_PREF, "") ?: ""

val SharedPreferences.apiKeyPref: String
    get() = getString(API_KEY_PREF, "") ?: ""

val SharedPreferences.accountBlockedTagsPref: Boolean
    get() = getBoolean(ACCOUNT_BLOCKED_TAGS_PREF, false)
