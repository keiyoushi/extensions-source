package eu.kanade.tachiyomi.extension.en.manhwaxxl

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy

class ManhwaXXL : ParsedHttpSource() {

    override val name = "Manhwa XXL"

    override val lang = "en"

    override val baseUrl = "https://hentaitnt.net"

    override val supportsLatest = true

    // Site changed from BakaManga
    override val versionId = 2

    private val json: Json by injectLazy()

    override fun headersBuilder() = super.headersBuilder().add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/hot-releases" + (if (page > 1) "/page/$page" else ""))

    override fun popularMangaSelector() = "a.comic-tmb"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.attr("title")
        thumbnail_url = element.selectFirst("img")?.absUrl("data-src")
    }

    override fun popularMangaNextPageSelector() = "ul.pagination li.active:not(:last-child)"

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/latest" + (if (page > 1) "/page/$page" else ""))

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) {
                addQueryParameter("s", query)
            } else {
                val genreFilter = filters.find { it is GenreFilter } as GenreFilter
                val genreId = genreFilter.genres[genreFilter.state].id
                addPathSegment("genre")
                addPathSegment(genreId)
            }
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        author = document.selectFirst("div.comic-details__item_links span")?.ownText()
        description = document.selectFirst("section#post-decription h2 + p")?.text()
        genre = document.select("div.comic-details__item a[href*=genre]").joinToString { it.text() }
        status = when (document.selectFirst("div.card h5")?.text()) {
            "Completed" -> SManga.ONGOING
            "Ongoing" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // Manga details page have paginated chapter list. We sacrifice `date_upload`
    // but we save a bunch of calls, since each page is like 12 chapters.
    override fun chapterListParse(response: Response): List<SChapter> {
        val detailsDocument = response.asJsoup()
        val script = detailsDocument.selectFirst("script:containsData(post_id)")?.data()
            ?: throw Exception("Failed to get chapter id")
        val data = script.substringAfter("viewsCacheL10n = ").substringBefore(";")
            .let { json.parseToJsonElement(it).jsonObject }

        val form = FormBody.Builder().add("action", "baka_ajax").add("type", "get_chapters_list")
            .add("id", data.getContent("post_id"))
            .add("chapters_list_nonce", data.getContent("nonce")).build()

        val ajaxResponse = client.newCall(
            POST("$baseUrl/wp-admin/admin-ajax.php", headers, form),
        ).execute()

        val jsonObject = json.decodeFromString<JsonObject>(ajaxResponse.body.string())
        return jsonObject["data"]!!.jsonObject["chapters"]!!.jsonArray.map {
            SChapter.create().apply {
                setUrlWithoutDomain(it.getContent("link"))
                name = it.getContent("title")
            }
        }
    }

    private fun JsonElement?.getContent(key: String): String {
        return this?.jsonObject?.get(key)?.jsonPrimitive?.content
            ?: throw Exception("Key $key not found")
    }

    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()

    override fun pageListParse(document: Document): List<Page> {
        return document.select("section#viewer img").mapIndexed { i, it ->
            Page(i, imageUrl = it.absUrl("src"))
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        Filter.Header("Ignored if using text search"),
        GenreFilter(getGenreList()),
    )

    private data class Genre(val name: String, val id: String) {
        override fun toString() = name
    }

    private class GenreFilter(val genres: Array<Genre>) :
        Filter.Select<String>("Genre", genres.map { it.name }.toTypedArray())

    // If you want to add new genres just add the name and id. (eg. https://hentaitnt.net/genre/action) action is the id
    // You can search more here: https://hentaitnt.net/genres
    private fun getGenreList() = arrayOf(
        Genre("All", ""),
        Genre("Action", "action"),
        Genre("Adult", "adult"),
        Genre("BL", "bl"),
        Genre("Comedy", "comedy"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Harem", "harem"),
        Genre("Horror", "horror"),
        Genre("Manga", "manga"),
        Genre("Manhwa", "manhwa"),
        Genre("Mature", "mature"),
        Genre("NTR", "ntr"),
        Genre("Romance", "romance"),
        Genre("Uncensore", "uncensore"),
        Genre("Webtoon", "webtoon"),
    )
}
