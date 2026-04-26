@file:Suppress("unused", "SpellCheckingInspection")

package eu.kanade.tachiyomi.extension.en.cmanhua

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.jsonInstance
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class CManhua : HttpSource() {

    override val name = "CManhua"
    override val baseUrl = "https://cmanhua.com"
    override val lang = "en"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular =====================================

    override fun popularMangaRequest(page: Int): Request = listRequest(page, sort = SORT_TOP_VIEWS)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaList(response)

    // ============================== Latest ======================================

    override fun latestUpdatesRequest(page: Int): Request = listRequest(page, sort = SORT_UPDATE_TIME)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaList(response)

    // ============================== Search ======================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            return searchRequest(page, query.trim())
        }

        val status = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart() ?: STATUS_OPTIONS.first().second
        val sort = filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: SORT_OPTIONS.first().second
        val minChapters = filters.firstInstanceOrNull<ChapterCountFilter>()?.toUriPart() ?: CHAPTER_OPTIONS.first().second
        val gender = filters.firstInstanceOrNull<GenderFilter>()?.toUriPart() ?: GENDER_OPTIONS.first().second
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.state?.filter { it.state }?.joinToString { it.id } ?: ""

        return listRequest(
            page = page,
            sort = sort,
            status = status,
            minChapters = minChapters,
            gender = gender,
            genres = genres,
        )
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaList(response)

    // ============================== Manga Details ===============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.title-detail")?.text() ?: throw Exception("Title not found")
            author = document.select("li.author p.col-xs-8 a").joinToString { it.text() }
            status = document.selectFirst("li.status p.col-xs-8")?.text().orEmpty().toStatus()
            genre = document.select("li.kind p.col-xs-8 a").joinToString { it.text() }
            description = document.selectFirst("#descript")?.text()
            thumbnail_url = document.selectFirst("div.col-image img")?.absUrl("src")
        }
    }

    // ============================== Chapters ====================================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("#listchap li.row").map { element ->
            val link = element.selectFirst("div.chapter a") ?: throw Exception("Chapter link not found")
            val name: String = link.text()
            val date: String = element.selectFirst("time[datetime]")?.attr("datetime").orEmpty()

            SChapter.create().apply {
                this.name = name
                setUrlWithoutDomain(link.absUrl("href"))
                date_upload = dateFormat.tryParse(date)
                chapter_number = parseChapterNumber(name)
            }
        }
    }

    // ============================== Pages =======================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val encoded: String = document.select("script")
            .asSequence()
            .mapNotNull { CHAPTER_TOKEN_REGEX.find(it.data())?.groupValues?.get(1) }
            .firstOrNull()
            ?: throw Exception("Unable to find chapter token")

        return fetchChapterPages(encoded, response.request.url.toString())
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headers)

    // ============================== Filters =====================================

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Filters are ignored when searching by name."),
        SortFilter(),
        StatusFilter(),
        ChapterCountFilter(),
        GenderFilter(),
        GenreFilter(GENRES),
    )

    // ============================== Request Builders ============================

    private fun listRequest(
        page: Int,
        sort: String,
        status: String = STATUS_OPTIONS.first().second,
        minChapters: String = CHAPTER_OPTIONS.first().second,
        gender: String = GENDER_OPTIONS.first().second,
        genres: String = "",
    ): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("danhsach/P$page/index.html")
            .addQueryParameter("status", status)
            .addQueryParameter("sort", sort)
            .addQueryParameter("chapter", minChapters)
            .addQueryParameter("gender", gender)
            .apply {
                if (genres.isNotEmpty()) {
                    addQueryParameter("spec", genres)
                }
            }
            .build()

        return GET(url, headers)
    }

    private fun searchRequest(page: Int, query: String): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment(query)
            .apply {
                if (page > 1) {
                    addPathSegment("P$page")
                }
            }
            .addPathSegment("tim-kiem.html")
            .build()

        return GET(url, headers)
    }

    private fun chapterApiRequest(encoded: String, referer: String): Request {
        val body = buildJsonObject {
            put("enc", encoded)
        }.toJsonRequestBody()

        return POST(
            "$baseUrl/Service.asmx/getchapter",
            headersBuilder()
                .add("X-Requested-With", "XMLHttpRequest")
                .set("Referer", referer)
                .build(),
            body,
        )
    }

    // ============================== Utilities ===================================

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangaList = document.select("ul.lst_story li.item").mapNotNull { element ->
            val titleElement = element.selectFirst("h3 a") ?: element.selectFirst("a[itemprop=url]")

            val url: String = titleElement?.absUrl("href") ?: return@mapNotNull null
            if (url.isEmpty()) return@mapNotNull null

            val title: String = titleElement.text()
            val thumbnail: String = element.selectFirst("img")?.let { image ->
                image.absUrl("data-src").ifEmpty { image.absUrl("src") }
            }.orEmpty()

            SManga.create().apply {
                this.title = title
                if (thumbnail.isNotEmpty()) {
                    thumbnail_url = thumbnail
                }
                setUrlWithoutDomain(url)
            }
        }

        val page = pageFromUrl(response.request.url)
        val hasNextPage = hasNextPage(document, page)
        return MangasPage(mangaList, hasNextPage)
    }

    private fun pageFromUrl(url: HttpUrl): Int {
        val pageSegment = url.pathSegments.firstOrNull { it.startsWith("P") && it.length > 1 }
        return pageSegment?.substring(1)?.toIntOrNull() ?: 1
    }

    private fun hasNextPage(document: Document, page: Int): Boolean {
        val maxPage = document.select("li.list-pager a")
            .mapNotNull { it.text().toIntOrNull() }
            .maxOrNull()
            ?: return false

        return page < maxPage
    }

    private fun fetchChapterPages(encoded: String, referer: String): List<Page> {
        val request = chapterApiRequest(encoded, referer)
        return client.newCall(request).execute().use { response ->
            val body = response.body.string()
            val data = extractChapterPayload(body)
            val code = data.trim().toIntOrNull()
            if (code != null) {
                throw Exception(errorMessage(code))
            }

            Jsoup.parseBodyFragment(data, baseUrl).select("img").mapIndexed { index, image ->
                Page(index, imageUrl = image.absUrl("src"))
            }
        }
    }

    private fun extractChapterPayload(body: String): String {
        val trimmed = body.trim().removePrefix("\uFEFF")
        if (trimmed.isEmpty()) {
            throw Exception("Failed to load chapter pages.")
        }
        if (trimmed.startsWith("<") || trimmed.contains("<img", ignoreCase = true)) {
            return trimmed
        }

        val jsonElement = runCatching { jsonInstance.parseToJsonElement(trimmed) }.getOrNull()
            ?: throw Exception(errorMessageFromBody(trimmed))

        if (jsonElement !is JsonObject) {
            return jsonElement.jsonPrimitive.contentOrNull
                ?: throw Exception(errorMessageFromBody(trimmed))
        }

        val data: String = jsonElement["d"]?.jsonPrimitive?.contentOrNull
            ?: jsonElement["data"]?.jsonPrimitive?.contentOrNull
            ?: jsonElement["html"]?.jsonPrimitive?.contentOrNull
            ?: throw Exception(errorMessageFromBody(trimmed))

        return data
    }

    private fun errorMessageFromBody(body: String): String {
        val code = body.trim().toIntOrNull()
        if (code != null) {
            return errorMessage(code)
        }

        val jsonObject = runCatching { jsonInstance.parseToJsonElement(body) }.getOrNull() as? JsonObject
        val error = jsonObject
            ?.get("error")
            ?.jsonPrimitive
            ?.contentOrNull

        return error ?: "Failed to load chapter pages. Unknown error."
    }

    // ============================== Helpers =====================================

    private fun String.toStatus(): Int = when (lowercase(Locale.ROOT)) {
        "on going", "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun parseChapterNumber(name: String): Float = CHAPTER_NUMBER_REGEX.find(name)?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: -1f

    private fun errorMessage(code: Int): String = when (code) {
        3 -> "You do not have enough coins to unlock this chapter."
        -1 -> "Chapter does not exist."
        -2 -> "Chapter token expired."
        -3 -> "Invalid parameters."
        -4 -> "Login required to access this chapter."
        -5 -> "Account is banned or does not exist."
        else -> "Failed to load chapter pages. Unknown error (code: $code)."
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    companion object {
        private const val SORT_UPDATE_TIME = "0"
        private const val SORT_TOP_VIEWS = "2"

        private val CHAPTER_NUMBER_REGEX = Regex("""chapter\s*([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)
        private val CHAPTER_TOKEN_REGEX = Regex("""var\s+ts\s*=\s*"([^"]+)""")
    }
}
