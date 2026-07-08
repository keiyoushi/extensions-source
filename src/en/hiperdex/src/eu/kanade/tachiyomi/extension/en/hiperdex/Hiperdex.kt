package eu.kanade.tachiyomi.extension.en.hiperdex

import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.Toast
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.lib.randomua.addRandomUAPreference
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferences
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Hiperdex :
    Madara(),
    ConfigurableSource {
    override val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)

    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div.summary-content"

    private val preferences = getPreferences()

    override val client = super.client.newBuilder()
        .addNetworkInterceptor(ClearanceInterceptor())
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .setRandomUserAgent()

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val pageListParseSelector = "div.page-break:not([style*='display:none'])"

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val noRemoveTitleBrowsingPref = CheckBoxPreference(screen.context).apply {
            key = NO_REMOVE_TITLE_BROWSING_PREF
            title = "Don't apply title cleaning in browsing/search results"
            summary = "Don't apply the 2 options above when browsing or searching for manga, but still apply them in manga details."
            setVisible(isRemoveTitleVersion() || customRemoveTitle().isNotEmpty())
            setDefaultValue(false)
        }

        CheckBoxPreference(screen.context).apply {
            key = "${REMOVE_TITLE_VERSION_PREF}_$lang"
            title = "Remove version information from entry titles"
            summary = "This removes version tags like '(Official)' or '(Uncensored)' from entry titles " +
                "and helps identify duplicate entries in your library. " +
                "To update existing entries, remove them from your library (unfavorite) and refresh manually. " +
                "You might also want to clear the database in advanced settings."
            setDefaultValue(false)
            setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                noRemoveTitleBrowsingPref.setVisible(enabled || customRemoveTitle().isNotEmpty())
                true
            }
        }.also { screen.addPreference(it) }

        EditTextPreference(screen.context).apply {
            key = "${REMOVE_TITLE_CUSTOM_PREF}_$lang"
            title = "Custom regex to be removed from title"
            summary = customRemoveTitle()
            setDefaultValue("")

            val validate = { str: String ->
                runCatching { Regex(str) }
                    .map { true to "" }
                    .getOrElse { false to it.message }
            }

            setOnBindEditTextListener { editText ->
                editText.addTextChangedListener(
                    object : TextWatcher {
                        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

                        override fun afterTextChanged(editable: Editable?) {
                            editable ?: return
                            val text = editable.toString()
                            val valid = validate(text)
                            editText.error = if (!valid.first) valid.second else null
                            editText.rootView.findViewById<Button>(android.R.id.button1)?.isEnabled = editText.error == null
                        }
                    },
                )
            }

            setOnPreferenceChangeListener { _, newValue ->
                val (isValid, message) = validate(newValue as String)
                if (isValid) {
                    summary = newValue
                    noRemoveTitleBrowsingPref.setVisible(isRemoveTitleVersion() || newValue.isNotEmpty())
                } else {
                    Toast.makeText(screen.context, message, Toast.LENGTH_LONG).show()
                }
                isValid
            }
        }.also { screen.addPreference(it) }

        screen.addPreference(noRemoveTitleBrowsingPref)

        screen.addRandomUAPreference()
    }

    override fun popularMangaFromElement(element: Element): SManga = super.popularMangaFromElement(element).apply {
        if (!noCleanTitlesWhileBrowsing()) {
            title = title.cleanTitleIfNeeded()
        }
    }

    override fun latestUpdatesFromElement(element: Element): SManga = super.latestUpdatesFromElement(element).apply {
        if (!noCleanTitlesWhileBrowsing()) {
            title = title.cleanTitleIfNeeded()
        }
    }

    override fun searchMangaFromElement(element: Element): SManga = super.searchMangaFromElement(element).apply {
        if (!noCleanTitlesWhileBrowsing()) {
            title = title.cleanTitleIfNeeded()
        }
    }

    override fun searchMangaSelector() = "#loop-content div.page-listing-item"

    override val chapterUrlSuffix = ""

    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        val cleanedTitle = title.cleanTitleIfNeeded()
        if (cleanedTitle != title.trim()) {
            description = listOfNotNull(title, description)
                .joinToString("\n\n")
            title = cleanedTitle
        }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    private fun String.cleanTitleIfNeeded(): String {
        var tempTitle = this
        customRemoveTitle().takeIf { it.isNotEmpty() }?.let { customRegex ->
            runCatching {
                tempTitle = tempTitle.replace(Regex(customRegex), "")
            }
        }
        if (isRemoveTitleVersion()) {
            tempTitle = tempTitle.replace(titleRegex, "")
        }
        return tempTitle.trim()
    }

    private fun isRemoveTitleVersion(): Boolean = preferences.getBoolean("${REMOVE_TITLE_VERSION_PREF}_$lang", false)
    private fun customRemoveTitle(): String = preferences.getString("${REMOVE_TITLE_CUSTOM_PREF}_$lang", "")!!

    private fun noCleanTitlesWhileBrowsing(): Boolean = preferences.getBoolean(NO_REMOVE_TITLE_BROWSING_PREF, false)

    companion object {
        private const val REMOVE_TITLE_VERSION_PREF = "REMOVE_TITLE_VERSION"
        private const val REMOVE_TITLE_CUSTOM_PREF = "REMOVE_TITLE_CUSTOM"
        private const val NO_REMOVE_TITLE_BROWSING_PREF = "NO_REMOVE_TITLE_BROWSING"

        private val titleRegex: Regex by lazy {
            Regex(
                """^(?:\s*(?:\([^()]*\)|\{[^{}]*\}|\[(?:(?!]).)*]|«[^»]*»|〘[^〙]*〙|「[^」]*」|『[^』]*』|≪[^≫]*≫|﹛[^﹜]*﹜|〖[^〖〗]*〗|𖤍.+?𖤍|《[^》]*》|⌜.+?⌝|⟨[^⟩]*⟩)\s*)+|(?:\s*(?:\([^()]*\)|\{[^{}]*\}|\[(?:(?!]).)*]|«[^»]*»|〘[^〙]*〙|「[^」]*」|『[^』]*』|≪[^≫]*≫|﹛[^﹜]*﹜|〖[^〖〗]*〗|𖤍.+?𖤍|《[^》]*》|⌜.+?⌝|⟨[^⟩]*⟩|/\s*Official)\s*)+$""",
                RegexOption.IGNORE_CASE,
            )
        }
    }
}
