package eu.kanade.tachiyomi.extension.es.lectorjpg

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class LectorJpg : Madara(
    "LectorJPG",
    "https://lectorjpg.com",
    "es",
    dateFormat = SimpleDateFormat("d MMMM, yyyy", Locale("es")),
) {

    override val versionId = 2

    override val mangaSubString = "serie"

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val client = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 3, 1)
        .build()

    override fun popularMangaSelector() = "div:not([class]):has(> div.break-words)"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h3")!!.text()
        thumbnail_url = element.selectFirst("img")?.let { imageFromElement(it) }
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
    }

    override fun searchMangaSelector() = "button.group > div.grid"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h3")!!.text()
        thumbnail_url = element.selectFirst("div[style].bg-cover")?.let { imageFromElement(it) }
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
    }

    override val mangaDetailsSelectorTitle = "div.wp-manga div.grid > h1"
    override val mangaDetailsSelectorStatus = "div.wp-manga div[alt=type]:eq(0) > span"
    override val mangaDetailsSelectorGenre = "div.wp-manga div[alt=type]:gt(0) > span"
    override val mangaDetailsSelectorDescription = "div.wp-manga div#expand_content"
    override val mangaDetailsSelectorThumbnail = "div.grid.border div.bg-cover"

    override fun chapterListSelector() = "ul#list-chapters li > a"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = element.selectFirst("div.grid > span")!!.text()
        date_upload = element.selectFirst("div.grid > div")?.text()?.let { parseChapterDate(it) } ?: 0
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
    }

    override fun parseGenres(document: Document): List<Genre> {
        return document.select("div:has(> input[type=checkbox])")
            .orEmpty()
            .map { li ->
                Genre(
                    li.selectFirst("label")!!.text(),
                    li.selectFirst("input[type=checkbox]")!!.`val`(),
                )
            }
    }

    override fun imageFromElement(element: Element): String? {
        return when {
            element.hasAttr("data-src") -> element.attr("abs:data-src")
            element.hasAttr("data-lazy-src") -> element.attr("abs:data-lazy-src")
            element.hasAttr("srcset") -> element.attr("abs:srcset").getSrcSetImage()
            element.hasAttr("data-cfsrc") -> element.attr("abs:data-cfsrc")
            element.hasAttr("style") -> element.attr("style").substringAfter("url(").substringBefore(")")
            else -> element.attr("abs:src")
        }
    }
}
