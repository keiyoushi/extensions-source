package eu.kanade.tachiyomi.extension.en.mangadistrict

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaDistrict :
    Madara(
        "Manga District",
        "https://mangadistrict.com",
        "en",
    ),
    ConfigurableSource {

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun searchMangaNextPageSelector() = "div[role=navigation] span.current + a.page"

    private val titleComment = Regex("\\(.*\\)")

    override fun popularMangaFromElement(element: Element): SManga {
        return super.popularMangaFromElement(element).apply {
            if (preferences.getBoolean(REMOVE_TITLE_COMMENT_PREF, true)) {
                title = this.title.replace(titleComment, "").trim()
            }
        }
    }

    override fun searchMangaFromElement(element: Element): SManga {
        return super.searchMangaFromElement(element).apply {
            if (preferences.getBoolean(REMOVE_TITLE_COMMENT_PREF, true)) {
                title = this.title.replace(titleComment, "").trim()
            }
        }
    }

    override fun mangaDetailsParse(document: Document): SManga {
        return super.mangaDetailsParse(document).apply {
            if (preferences.getBoolean(REMOVE_TITLE_COMMENT_PREF, true)) {
                title = this.title.replace(titleComment, "").trim()
            }
        }
    }

    private fun chaptersStripSelector(): String {
        return when (getImgResPref()) {
            "Full quality" -> "a:contains(High Quality) ~ ul.list-chap"
            "High quality" -> "a:contains(Full Quality) ~ ul.list-chap"
            else -> "dummy"
        }
    }

    private fun Response.stripElements(selector: String): Response {
        val contentType: MediaType? = body.contentType()
        val document = asJsoup()
        document.select(selector).remove()

        val body: ResponseBody = document.body().toString().toResponseBody(contentType)
        return newBuilder().body(body).build()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response.stripElements(chaptersStripSelector()))
    }

    private fun getImgResPref(): String? = preferences.getString(IMG_RES_PREF_KEY, IMG_RES_PREF_DEFAULT_VALUE)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = REMOVE_TITLE_COMMENT_PREF
            title = "Remove comments from title, e.g. \"(Official)\", \"(Doujinshi)\""
            summary = "To help app find duplicate entries in library"
            setDefaultValue(true)
        }.let(screen::addPreference)

        ListPreference(screen.context).apply {
            key = IMG_RES_PREF_KEY
            title = IMG_RES_PREF_TITLE
            entries = IMG_RES_PREF_ENTRIES
            entryValues = IMG_RES_PREF_ENTRY_VALUES
            setDefaultValue(IMG_RES_PREF_DEFAULT_VALUE)
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(IMG_RES_PREF_KEY, entry).commit()
            }
        }.let(screen::addPreference)
    }

    companion object {
        private const val REMOVE_TITLE_COMMENT_PREF = "REMOVE_TITLE_COMMENT"

        private const val IMG_RES_PREF_KEY = "IMG_RES"
        private const val IMG_RES_PREF_TITLE = "Image resolution"
        private val IMG_RES_PREF_ENTRIES = arrayOf(
            "Full quality",
            "High quality",
            "Both",
        )
        private val IMG_RES_PREF_ENTRY_VALUES = IMG_RES_PREF_ENTRIES
        private val IMG_RES_PREF_DEFAULT_VALUE = IMG_RES_PREF_ENTRY_VALUES[0]
    }
}
