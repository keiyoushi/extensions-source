package eu.kanade.tachiyomi.extension.en.mangadistrict

import android.content.SharedPreferences
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.Toast
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.utils.getPreferencesLazy
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class MangaDistrict :
    MadaraNoAjax(),
    ConfigurableSource {

    override val mangaSubString = "series"
    override val genreDirectory = "publication-genre"
    override fun nextPageSelector() = ".wp-pagenavi a.last"

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun archiveManga(element: Element, id: String) = super.archiveManga(element, id)?.let {
        it.takeIf { noCleanTitlesWhileBrowsing() }
            ?: it.cleanTitleIfNeeded()
    }

    override fun parseDetails(document: Document, id: String, preserveUrl: String?) = super.parseDetails(document, id, preserveUrl)
        .cleanTitleIfNeeded()

    override fun parseChapterList(document: Document, mangaPath: String): List<SChapter> {
        val chapters = super.parseChapterList(document, mangaPath)
        return when (getImgRes()) {
            IMG_RES_HIGH -> chapters.filterNot { it.url.contains("/v2-full-quality") }
            IMG_RES_FULL -> chapters.filterNot { it.url.contains("/v1-high-quality") }
            else -> chapters
        }
    }

    override val chapterDateSelector = ".chapter-release-date .timediff"

    override val pageListParseSelector = "div.page-break img:not(noscript img):not(#image-99999)"

    private fun SManga.cleanTitleIfNeeded() = apply {
        title = title.let { originalTitle ->
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
    }

    private fun isRemoveTitleVersion() = preferences.getBoolean(REMOVE_TITLE_VERSION_PREF, false)
    private fun customRemoveTitle(): String = preferences.getString("${REMOVE_TITLE_CUSTOM_PREF}_$lang", "")!!
    private fun noCleanTitlesWhileBrowsing(): Boolean = preferences.getBoolean(NO_REMOVE_TITLE_BROWSING_PREF, false)
    private fun getImgRes() = preferences.getString(IMG_RES_PREF, IMG_RES_DEFAULT)!!

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val noRemoveTitleBrowsingPref = CheckBoxPreference(screen.context).apply {
            key = NO_REMOVE_TITLE_BROWSING_PREF
            title = "Don't apply title cleaning in browsing/search results"
            summary = "Don't apply the 2 options above when browsing or searching for manga, but still apply them in manga details."
            setVisible(isRemoveTitleVersion() || customRemoveTitle().isNotEmpty())
            setDefaultValue(false)
        }

        CheckBoxPreference(screen.context).apply {
            key = REMOVE_TITLE_VERSION_PREF
            title = "Remove version information from entry titles"
            summary = "This removes version tags like “(Official)” or “(Doujinshi)” from entry titles " +
                "and helps identify duplicate entries in your library. " +
                "To update existing entries, remove them from your library (unfavorite) and refresh manually. " +
                "You might also want to clear the database in advanced settings."
            setDefaultValue(false)
            setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                noRemoveTitleBrowsingPref.setVisible(enabled || customRemoveTitle().isNotEmpty())
                true
            }
        }.let(screen::addPreference)

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
        }.let(screen::addPreference)

        screen.addPreference(noRemoveTitleBrowsingPref)

        ListPreference(screen.context).apply {
            key = IMG_RES_PREF
            title = "Image quality"
            entries = arrayOf("All", "High quality", "Full quality")
            entryValues = arrayOf(IMG_RES_ALL, IMG_RES_HIGH, IMG_RES_FULL)
            summary = "%s\nRefresh entry to update the chapter list."
            setDefaultValue(IMG_RES_DEFAULT)
        }.let(screen::addPreference)
    }

    override fun imageFromElement(element: Element): String? = when {
        element.hasAttr("data-wpfc-original-src") -> element.attr("abs:data-wpfc-original-src")
        else -> super.imageFromElement(element)
    }

    companion object {
        private val titleRegex: Regex by lazy {
            Regex(
                """^(?:\s*(?:\([^()]*\)|\{[^{}]*\}|\[(?:(?!]).)*]|«[^»]*»|〘[^〙]*〙|「[^」]*」|『[^』]*』|≪[^≫]*≫|﹛[^﹜]*﹜|〖[^〖〗]*〗|𖤍.+?𖤍|《[^》]*》|⌜.+?⌝|⟨[^⟩]*⟩)\s*)+|(?:\s*(?:\([^()]*\)|\{[^{}]*\}|\[(?:(?!]).)*]|«[^»]*»|〘[^〙]*〙|「[^」]*」|『[^』]*』|≪[^≫]*≫|﹛[^﹜]*﹜|〖[^〖〗]*〗|𖤍.+?𖤍|《[^》]*》|⌜.+?⌝|⟨[^⟩]*⟩|/\s*Official)\s*)+$""",
                RegexOption.IGNORE_CASE,
            )
        }

        private const val REMOVE_TITLE_VERSION_PREF = "REMOVE_TITLE_VERSION"
        private const val REMOVE_TITLE_CUSTOM_PREF = "REMOVE_TITLE_CUSTOM"
        private const val NO_REMOVE_TITLE_BROWSING_PREF = "NO_REMOVE_TITLE_BROWSING"

        private const val IMG_RES_PREF = "IMG_RES"
        private const val IMG_RES_ALL = "all"
        private const val IMG_RES_HIGH = "high"
        private const val IMG_RES_FULL = "full"
        private const val IMG_RES_DEFAULT = IMG_RES_ALL
    }
}
