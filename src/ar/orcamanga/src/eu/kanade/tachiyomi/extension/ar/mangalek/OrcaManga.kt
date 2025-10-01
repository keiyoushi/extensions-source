package eu.kanade.tachiyomi.extension.ar.orcamanga

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt

class OrcaManga : ParsedHttpSource(), ConfigurableSource {

    override val name = "OrcaManga"
    override val lang = "ar"
    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0)
    }

    override val baseUrl: String
        get() = preferences.getString(BASE_URL_PREF, DEFAULT_BASE_URL)!!

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val baseUrlPref = EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = "Base URL"
            summary = "الرابط الأساسي للموقع (غيّره لو الموقع نقل)"
            dialogTitle = "غيّر رابط الموقع"
            setDefaultValue(DEFAULT_BASE_URL)
        }
        screen.addPreference(baseUrlPref)
    }

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/manga-list?page=$page", headers)

    override fun popularMangaSelector() = "div.anime-card"
    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.select("a").attr("href"))
        manga.title = element.select("h3.anime-title").text()
        manga.thumbnail_url = element.select("img").attr("src")
        return manga
    }
    override fun popularMangaNextPageSelector() = "a.next"

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/filterlist?page=$page&sort=update", headers)

    override fun latestUpdatesSelector() = "div.anime-card"
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector() = "a.next"

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.title = document.select("h1.anime-title").text()
        manga.author = document.select(".author a").text()
        manga.genre = document.select(".genres a").joinToString { it.text() }
        manga.description = document.select(".description").text()
        manga.thumbnail_url = document.select(".anime-cover img").attr("src")
        return manga
    }

    override fun chapterListSelector() = "ul.episodes li"
    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.select("a").attr("href"))
        chapter.name = element.select("a").text()
        return chapter
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.reader-images img").mapIndexed { i, el ->
            Page(i, "", el.attr("src"))
        }
    }

    override fun imageUrlParse(document: Document): String = document.select("img").attr("src")

    companion object {
        private const val BASE_URL_PREF = "base_url"
        private const val DEFAULT_BASE_URL = "https://orcamanga.site"
    }
}
