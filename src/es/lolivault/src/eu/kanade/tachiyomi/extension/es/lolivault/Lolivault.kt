package eu.kanade.tachiyomi.extension.es.lolivault

import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MultipartBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Lolivault : ParsedHttpSource() {
    override val name = "Lolivault"

    override val baseUrl = "https://lector.lolivault.net"

    override val lang = "es"

    override val supportsLatest = false

    override val client = network.cloudflareClient

    // Latest

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()

    override fun latestUpdatesSelector() = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector() = null

    // Popular

    override fun popularMangaSelector() = "div.group"

    override fun popularMangaRequest(page: Int) = POST(
        "$baseUrl/directory/$page",
        headers,
        getFormData().build(),
    )

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        element.select(".title > a").let {
            manga.title = it.attr("title")
            manga.setUrlWithoutDomain(it.attr("href"))
        }
        manga.thumbnail_url = element.select("img").attr("src")

        return manga
    }

    override fun popularMangaNextPageSelector() = ".next > .gbutton:last-of-type"

    // Search

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = POST(
        "$baseUrl/search",
        headers,
        getFormData()
            .addFormDataPart("search", query)
            .build(),
    )

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        element.select(".title > a").let {
            manga.title = it.attr("title")
            manga.setUrlWithoutDomain(it.attr("href"))
        }

        return manga
    }

    override fun searchMangaNextPageSelector() = null

    // Details

    override fun mangaDetailsRequest(manga: SManga) = POST(
        "$baseUrl/${manga.url}",
        headers,
        getFormData().build(),
    )

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()

        manga.title = document.select(".info > ul > li:nth-of-type(1)").text().replace("Título: ", "")
        manga.description = document.select(".info > ul > li:nth-of-type(4)").text().replace("Descripción: ", "")
        manga.thumbnail_url = document.select(".thumbnail > img").attr("src")
        manga.status = SManga.COMPLETED
        manga.update_strategy = UpdateStrategy.ONLY_FETCH_ONCE

        return manga
    }

    // Chapter

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListSelector() = ".group > .element"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        element.select(".title > a").let {
            chapter.name = it.attr("title")
            chapter.setUrlWithoutDomain(it.attr("href"))
        }
        element.select(".meta_r").let {
            chapter.scanlator = it.select("a").joinToString(", ") { it1 -> it1.attr("title") }
            chapter.date_upload = it.text().split(", ").last().let { date ->
                SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).parse(date)?.time ?: 0L
            }
        }

        return chapter
    }

    // Page list

    override fun pageListRequest(chapter: SChapter) =
        POST("$baseUrl/${chapter.url}", headers, getFormData().build())

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        val html = document.body().html()
        val startIndex = html.indexOf("[{")
        val endIndex = html.indexOf("}]") + 2
        val jsonPages = html.substring(startIndex, endIndex)

        val chapterPages: JsonArray = Json.decodeFromString(jsonPages)

        chapterPages.forEachIndexed { index, page ->
            pages.add(Page(index, imageUrl = page.jsonObject["url"]!!.jsonPrimitive.content))
        }

        return pages
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    private fun getFormData() = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("adult", "true")
}
