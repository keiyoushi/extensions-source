package eu.kanade.tachiyomi.extension.en.hiperdex

import android.widget.Toast
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.getPreferences
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class Hiperdex :
    Madara(
        "Hiperdex",
        "https://hiperdex.com",
        "en",
        dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.US),
    ),
    ConfigurableSource {

    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div.summary-content"

    private val preferences = getPreferences()

    override val baseUrl by lazy { getPrefBaseUrl() }

    override val client = super.client.newBuilder()
        .setRandomUserAgent(
            preferences.getPrefUAType(),
            preferences.getPrefCustomUA(),
        )
        .rateLimit(3)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = BASE_URL_PREF_TITLE
            summary = BASE_URL_PREF_SUMMARY
            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "Default URL:\n\t${super.baseUrl}"
            setDefaultValue(super.baseUrl)
            setOnPreferenceChangeListener { _, newValue ->
                Toast.makeText(screen.context, RESTART_APP_MESSAGE, Toast.LENGTH_LONG).show()
                true
            }
        }.also { screen.addPreference(it) }

        CheckBoxPreference(screen.context).apply {
            key = "${REMOVE_TITLE_VERSION_PREF}_$lang"
            title = "Remove version information from entry titles"
            summary = "This removes version tags like '(Official)' or '(Uncensored)' from entry titles " +
                "and helps identify duplicate entries in your library. " +
                "To update existing entries, remove them from your library (unfavorite) and refresh manually. " +
                "You might also want to clear the database in advanced settings."
            setDefaultValue(false)
        }.also { screen.addPreference(it) }

        EditTextPreference(screen.context).apply {
            key = "${REMOVE_TITLE_CUSTOM_PREF}_$lang"
            title = "Custom regex to be removed from title"
            summary = preferences.getString("${REMOVE_TITLE_CUSTOM_PREF}_$lang", "") ?: ""
            setDefaultValue("")
            setOnPreferenceChangeListener { _, newValue ->
                runCatching {
                    Regex(newValue as String)
                }.onFailure {
                    Toast.makeText(screen.context, it.message, Toast.LENGTH_LONG).show()
                }.isSuccess
            }
        }.also { screen.addPreference(it) }

        addRandomUAPreferenceToScreen(screen)
    }

    override fun searchMangaSelector() = "#loop-content div.page-listing-item"

    override fun mangaDetailsParse(document: Document): SManga {
        return super.mangaDetailsParse(document).apply {
            val cleanedTitle = title.let { originalTitle ->
                var tempTitle = originalTitle
                customRemoveTitle().takeIf { it.isNotEmpty() }?.let { customRegex ->
                    runCatching {
                        tempTitle = tempTitle.replace(Regex(customRegex), "")
                    }
                }
                if (isRemoveTitleVersion()) {
                    tempTitle = tempTitle.replace(titleRegex, "")
                }
                tempTitle.trim()
            }
            if (cleanedTitle != title) {
                description = listOfNotNull(title, description)
                    .joinToString("\n\n")
                title = cleanedTitle
            }
        }
    }

    private fun getPrefBaseUrl(): String = preferences.getString(BASE_URL_PREF, super.baseUrl)!!
    private fun isRemoveTitleVersion(): Boolean {
        return preferences.getBoolean("${REMOVE_TITLE_VERSION_PREF}_$lang", false)
    }
    private fun customRemoveTitle(): String =
        preferences.getString("${REMOVE_TITLE_CUSTOM_PREF}_$lang", "")!!

    init {
        preferences.getString(DEFAULT_BASE_URL_PREF, null).let { defaultBaseUrl ->
            if (defaultBaseUrl != super.baseUrl) {
                preferences.edit()
                    .putString(BASE_URL_PREF, super.baseUrl)
                    .putString(DEFAULT_BASE_URL_PREF, super.baseUrl)
                    .apply()
            }
        }
    }

    companion object {
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_TITLE = "Edit source URL (requires restart)"
        private const val BASE_URL_PREF_SUMMARY = "The default settings will be applied when the extension is next updated"
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val RESTART_APP_MESSAGE = "Restart app to apply new setting."
        private const val REMOVE_TITLE_VERSION_PREF = "REMOVE_TITLE_VERSION"
        private const val REMOVE_TITLE_CUSTOM_PREF = "REMOVE_TITLE_CUSTOM"

        private val titleRegex: Regex =
            Regex("\\([^()]*\\)|\\{[^{}]*\\}|\\[(?:(?!]).)*]|«[^»]*»|〘[^〙]*〙|「[^」]*」|『[^』]*』|≪[^≫]*≫|﹛[^﹜]*﹜|〖[^〖〗]*〗|\uD81A\uDD0D.+?\uD81A\uDD0D|《[^》]*》|⌜.+?⌝|⟨[^⟩]*⟩|/Official|/ Official", RegexOption.IGNORE_CASE)
    }
}
