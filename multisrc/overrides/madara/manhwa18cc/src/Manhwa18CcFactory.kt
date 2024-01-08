package eu.kanade.tachiyomi.extension.all.manhwa18cc

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Manhwa18CcFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        Manhwa18CcEN(),
        Manhwa18CcKO(),
        Manhwa18CcALL(),
    )
}

class Manhwa18CcALL : Manhwa18Cc("Manhwa18.cc", "https://manhwa18.cc", "all")

class Manhwa18CcEN : Manhwa18Cc("Manhwa18.cc", "https://manhwa18.cc", "en") {
    override fun popularMangaSelector() = "div.manga-item:not(:has(h3 a[title$='Raw']))"
}

class Manhwa18CcKO : Manhwa18Cc("Manhwa18.cc", "https://manhwa18.cc", "ko") {
    override fun popularMangaSelector() = "div.manga-item:has(h3 a[title$='Raw'])"
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/raw/$page")
}

abstract class Manhwa18Cc(
    override val name: String,
    override val baseUrl: String,
    lang: String,
) : Madara(name, baseUrl, lang, SimpleDateFormat("dd MMM yyyy", Locale.US)) {

    override val fetchGenres = false

    override fun popularMangaSelector() = "div.manga-item"

    override val popularMangaUrlSelector = "div.manga-item div.data a"

    override fun popularMangaNextPageSelector() = "ul.pagination li.next a"

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/webtoons/$page?orderby=trending")

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/webtoons/$page?orderby=latest")

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // After searching and go back to popular page, it always sent empty query thus display
        // "No results found" message. So this fix redirect to popular page.
        if (query.isBlank()) return popularMangaRequest(page)

        return GET("$baseUrl/search?q=$query&page=$page")
    }

    override val mangaSubString = "webtoon"

    override val mangaDetailsSelectorDescription = "div.panel-story-description div.dsct"

    override fun chapterListSelector() = "li.a-h"

    override fun chapterDateSelector() = "span.chapter-time"

    override val pageListParseSelector = "div.read-content img"

    override fun pageListParse(document: Document): List<Page> {
        return document.select(pageListParseSelector).mapIndexed { index, element ->
            Page(
                index,
                document.location(),
                element?.let {
                    it.absUrl(if (it.hasAttr("data-src")) "data-src" else "src")
                },
            )
        }
    }
}
