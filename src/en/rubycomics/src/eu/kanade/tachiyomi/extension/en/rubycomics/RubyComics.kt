package eu.kanade.tachiyomi.extension.en.rubycomics

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.textOrNull
import org.jsoup.nodes.Document

@Source
abstract class RubyComics :
    Madara(),
    ConfigurableSource {

    override val chapterMode = ChapterMode.MangaAjax
    override val mangaDetailsSelectorDescription = "div.description-summary div.limit-html"

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun processThumbnail(url: String?, fromSearch: Boolean) = url?.replace(THUMBNAIL_SIZE_REGEX, "$1")

    override fun parseDetails(document: Document, id: String, preserveUrl: String?) = super.parseDetails(document, id, preserveUrl).apply {
        document.selectFirst("#averagerate")?.textOrNull()?.let { rating ->
            description = listOfNotNull("**Rating:** $rating", description).joinToString("\n\n")
        }
    }

    override fun parseChapterList(document: Document, mangaPath: String): List<SChapter> {
        val chapters = super.parseChapterList(document, mangaPath)
        if (!preferences.getBoolean(PREF_DEDUPLICATE_CHAPTERS, false)) return chapters
        // SPOILER rows are one-image previews, RAW rows untranslated
        return chapters.filterNot { it.name.contains(VARIANT_REGEX) }.distinctBy { it.name }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_DEDUPLICATE_CHAPTERS
            title = "Deduplicate chapters"
            summary = "Hide SPOILER previews, RAW uploads and repeated uploads of a chapter"
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    companion object {
        private const val PREF_DEDUPLICATE_CHAPTERS = "pref_deduplicate_chapters"
        private val THUMBNAIL_SIZE_REGEX = Regex("""-\d+x\d+(\.[a-zA-Z]+)$""")
        private val VARIANT_REGEX = Regex("""\b(?:SPOILER|RAW)\b""")
    }
}
