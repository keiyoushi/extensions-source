package eu.kanade.tachiyomi.extension.all.manhwa18net

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Manhwa18Net : ParsedHttpSource() {

    override val name = "Manhwa18Net"
    override val baseUrl = "https://manhwa18.net"
    override val lang = "all"
    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(3)
        .build()

    private fun extractPageJson(document: Document): JsonObject? {
        val app = document.selectFirst("#app") ?: return null
        val data = app.attr("data-page")
        if (data.isBlank()) return null
        return data.parseAs<JsonObject>()
    }

    private fun extractProps(document: Document): JsonObject? = extractPageJson(document)?.get("props")?.jsonObject

    // ============================================================
    // REQUESTS
    // ============================================================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga-list?sort=top&page=$page", headers)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga-list?sort=update&page=$page", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val builder = if (query.isNotEmpty()) {
            "$baseUrl/tim-kiem".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
        } else {
            "$baseUrl/manga-list".toHttpUrl().newBuilder()
        }

        builder.addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> builder.addQueryParameter("sort", filter.toUriPart())

                is StatusFilter -> {
                    filter.state.forEach { status ->
                        if (status.state) {
                            builder.addQueryParameter(status.uriParam, "1")
                        }
                    }
                }

                else -> {}
            }
        }

        return GET(builder.build().toString(), headers)
    }

    // ============================================================
    // FILTERS
    // ============================================================

    override fun getFilterList() = getFilters()

    // ============================================================
    // LIST PARSING
    // ============================================================

    override fun popularMangaParse(response: Response) = parseList(response)
    override fun latestUpdatesParse(response: Response) = parseList(response)
    override fun searchMangaParse(response: Response) = parseList(response)

    private fun parseList(response: Response): MangasPage {
        val document = Jsoup.parse(response.body.string())
        val props = extractProps(document) ?: return MangasPage(emptyList(), false)

        val listing = props["paginate"]?.jsonObject
            ?: props["popularManga"]?.jsonObject
            ?: props["mangas"]?.jsonObject
            ?: props["latestManhwaMain"]?.jsonObject
            ?: return MangasPage(emptyList(), false)

        val dataArray = listing["data"]?.jsonArray ?: return MangasPage(emptyList(), false)

        val mangas = dataArray.mapNotNull {
            val obj = it.jsonObject
            val slug = obj["slug"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val title = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null

            SManga.create().apply {
                this.title = title
                url = "/manga/$slug"
                thumbnail_url = fixImageUrl(
                    obj["cover_url"]?.jsonPrimitive?.contentOrNull
                        ?: obj["thumb_url"]?.jsonPrimitive?.contentOrNull,
                )
            }
        }

        val hasNextPage = listing["next_page_url"]?.jsonPrimitive?.contentOrNull != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================================================
    // DETAILS
    // ============================================================

    override fun mangaDetailsParse(document: Document): SManga {
        val props = extractProps(document) ?: return SManga.create()
        val manga = props["manga"]?.jsonObject ?: return SManga.create()

        return SManga.create().apply {
            title = manga["name"]?.jsonPrimitive?.contentOrNull ?: ""

            description =
                manga["pilot"]?.jsonPrimitive?.contentOrNull?.let { Jsoup.parse(it).text() }
                    ?: manga["description"]?.jsonPrimitive?.contentOrNull?.let { Jsoup.parse(it).text() }
                    ?: ""

            thumbnail_url = fixImageUrl(
                manga["cover_url"]?.jsonPrimitive?.contentOrNull
                    ?: manga["thumb_url"]?.jsonPrimitive?.contentOrNull,
            )

            genre = manga["genres"]?.jsonArray
                ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                ?.joinToString(", ")

            author = manga["artists"]?.jsonArray
                ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                ?.joinToString(", ")
                ?.ifEmpty { null }

            artist = author

            status = when (manga["status_id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()) {
                0 -> SManga.ONGOING
                1, 2 -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // ============================================================
    // CHAPTER LIST
    // ============================================================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = Jsoup.parse(response.body.string())
        val props = extractProps(document) ?: return emptyList()
        val manga = props["manga"]?.jsonObject ?: return emptyList()
        val slug = manga["slug"]?.jsonPrimitive?.contentOrNull ?: return emptyList()
        val chapters = props["chapters"]?.jsonArray ?: return emptyList()

        val chapterList = chapters.mapNotNull {
            val obj = it.jsonObject
            val chapSlug = obj["slug"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null

            SChapter.create().apply {
                name = obj["name"]?.jsonPrimitive?.contentOrNull ?: ""
                url = "/manga/$slug/$chapSlug"
            }
        }
        return chapterList
    }

    private fun extractChapterNumber(name: String): Float {
        // Extract chapter number from name like "Chapter 80" or "Chap 80"
        val regex = """(?:chapter|chap|ch)[^\d]*(\d+(?:\.\d+)?)""".toRegex(RegexOption.IGNORE_CASE)
        return regex.find(name)?.groupValues?.get(1)?.toFloatOrNull() ?: -1f
    }

    // ============================================================
    // PAGE LIST
    // ============================================================

    override fun pageListParse(document: Document): List<Page> {
        val root = extractPageJson(document) ?: return emptyList()
        val props = root["props"]?.jsonObject ?: return emptyList()
        val chapterContent = props["chapterContent"]?.jsonPrimitive?.contentOrNull
            ?: return emptyList()

        val contentDoc = Jsoup.parse(chapterContent)
        val images = contentDoc.select("img")

        return images.mapIndexedNotNull { index, img ->
            val src = img.attr("src")
                .ifBlank { img.attr("data-src") }
                .ifBlank { img.attr("data-lazy-src") }

            fixImageUrl(src)?.let {
                Page(index, "", it)
            }
        }
    }

    override fun imageRequest(page: Page): Request = GET(
        page.imageUrl!!,
        headersBuilder()
            .add("Referer", baseUrl)
            .build(),
    )

    // ============================================================
    // ABSTRACTS
    // ============================================================

    override fun popularMangaSelector() = throw UnsupportedOperationException()
    override fun popularMangaFromElement(element: Element) = throw UnsupportedOperationException()
    override fun popularMangaNextPageSelector() = throw UnsupportedOperationException()

    override fun latestUpdatesSelector() = throw UnsupportedOperationException()
    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()
    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()

    override fun searchMangaSelector() = throw UnsupportedOperationException()
    override fun searchMangaFromElement(element: Element) = throw UnsupportedOperationException()
    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException()

    override fun chapterListSelector() = throw UnsupportedOperationException()
    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    // ============================================================
    // UTIL
    // ============================================================

    private fun fixImageUrl(url: String?): String? = when {
        url.isNullOrBlank() -> null
        url.startsWith("http") -> url
        url.startsWith("/") -> baseUrl + url
        else -> "$baseUrl/$url"
    }
}
