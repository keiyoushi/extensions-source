package eu.kanade.tachiyomi.extension.en.mangahot

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.net.URLEncoder
import kotlin.math.ceil

class MangaHot : HttpSource() {

    override val name = "MangaHot"

    override val baseUrl = "https://mangahot.to"

    override val lang = "en"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder().apply {
        add("Referer", "$baseUrl/")
    }

    private val apiHeaders by lazy { apiHeadersBuilder().build() }

    private fun apiHeadersBuilder() = headersBuilder().apply {
        add("Accept", "*/*")
        set("Referer", "$baseUrl/list")
        add("Sec-Fetch-Dest", "empty")
        add("Sec-Fetch-Mode", "cors")
        add("Sec-Fetch-Site", "same-origin")
    }

    private val json: Json by injectLazy()

    private var currentTotalNumberOfPages = 1

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/api/list/latest?page=$page#$page", apiHeaders)

    override fun popularMangaParse(response: Response): MangasPage =
        searchMangaParse(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request =
        throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage =
        throw UnsupportedOperationException()

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api".toHttpUrl().newBuilder()
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val tag = (filterList.find { it is TagFilter } as TagFilter).toUriPart()

        if (page == 1) {
            setTotalNumberPages(query, tag)
        }

        return when {
            query.isNotBlank() -> {
                url.addPathSegments("search")
                url.fragment(page.toString())
                val body = buildJsonObject {
                    put("keyword", query)
                    put("page", page)
                    put("size", PAGE_LIMIT)
                }.toRequestBody()
                val headers = apiHeadersBuilder().apply {
                    set("Referer", "$baseUrl/search?q=${URLEncoder.encode(query, "UTF-8")}")
                }.build()
                POST(url.build().toString(), headers, body)
            }
            tag?.isNotBlank() == true -> {
                url.addPathSegments("tags")
                url.fragment(page.toString())
                val body = buildJsonObject {
                    put("keyword", tag)
                    put("page", page)
                    put("size", PAGE_LIMIT)
                }.toRequestBody()
                val headers = apiHeadersBuilder().apply {
                    add("Origin", baseUrl)
                    set("Referer", "$baseUrl/tags/${tag.replace(" ", "-")}")
                }.build()
                POST(url.build().toString(), headers, body)
            }
            else -> popularMangaRequest(page)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<MangaListDto>()
        data.data.total?.also {
            currentTotalNumberOfPages = ceil(it.toDouble() / PAGE_LIMIT).toInt()
        }

        val mangaList = data.data.listManga.map { it.toSManga(baseUrl) }

        val currentPage = response.request.url.encodedFragment!!.toInt()
        return MangasPage(mangaList, currentPage < currentTotalNumberOfPages)
    }

    private fun setTotalNumberPages(query: String, tag: String?) {
        val request = if (query.isNotBlank()) {
            GET("$baseUrl/search?q=${URLEncoder.encode(query, "UTF-8")}", headers)
        } else if (tag?.isNotBlank() == true) {
            GET("$baseUrl/tags/${tag.replace(" ", "-")}", headers)
        } else {
            currentTotalNumberOfPages = 1
            return
        }

        val document = client.newCall(request).execute().asJsoup()
        document.selectFirst("ul.ant-pagination > li:nth-last-child(2)")?.run {
            currentTotalNumberOfPages = text().toIntOrNull() ?: 1
        } ?: run {
            currentTotalNumberOfPages = 1
        }
    }

    // =============================== Filters ==============================

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Note: Ignored if using text search!"),
        Filter.Separator(),
        TagFilter(),
    )

    private class TagFilter : UriPartFilter(
        "Tag",
        arrayOf(
            Pair("<select>", ""),
            Pair("Action", "action-genre"),
            Pair("Adult", "adult-genre"),
            Pair("Adventure", "adventure-genre"),
            Pair("Doujinshi", "doujinshi-genre"),
            Pair("Drama", "drama-genre"),
            Pair("Ecchi", "ecchi-genre"),
            Pair("Fantasy", "fantasy-genre"),
            Pair("Gender Bender", "gender-bender-genre"),
            Pair("Girls Love", "girls-love-genre"),
            Pair("Hentai", "hentai-genre"),
            Pair("Isekai", "isekai-genre"),
            Pair("Manga", "manga"),
            Pair("Manhua", "manhua"),
            Pair("Manhwa", "manhwa"),
            Pair("Monsters", "monsters-genre"),
            Pair("Romance", "romance-genre"),
            Pair("School Life", "school life genre"),
            Pair("Sci-Fi", "sci fi genre"),
            Pair("Seinen", "seinen genre"),
        ),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String?>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    // =========================== Manga Details ============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text()
            author = document.getInfo("Author")
            genre = document.getInfo("Genre")
            status = document.getInfo("Status").parseStatus()
            description = buildString {
                document.selectFirst("div.pt-6:has(> div:contains(Description)) > div:nth-child(2)")?.let {
                    append(it.text())
                }
                append("\n\n")
                document.getInfo("Alt name")?.let {
                    append("Alt name: ")
                    append(it)
                }
            }.trim()
        }
    }

    private fun String?.parseStatus(): Int = when (this?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun Element.getInfo(name: String): String? =
        selectFirst("li:has(span:contains($name))")
            ?.ownText()
            ?.substringAfter(":")
            ?.trim()

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val html = response.body.string()

        val dataStr = CHAPTER_REGEX.find(html)?.groupValues?.get(1)
            ?.replace("\\\"", "\"")
            ?: throw Exception("Unable to find chapter data")
        val chapterList = json.decodeFromString<List<ChapterDto>>(dataStr)

        return chapterList.map {
            it.toSChapter(response.request.url.toString())
        }.reversed()
    }

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request {
        val id = chapter.url.substringAfterLast("#")
        val headers = apiHeaders.newBuilder()
            .set("Referer", chapter.url.substringBeforeLast("#"))
            .build()
        return GET("$baseUrl/api/chapter/$id", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<PagesDto>().data.chapter

        return data.resources.mapIndexed { index, image ->
            Page(index, imageUrl = "https://${data.cdnHost}/$image")
        }
    }

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val pageHeaders = headersBuilder().apply {
            add("Accept", "*/*")
            add("Host", page.imageUrl!!.toHttpUrl().host)
        }.build()

        return GET(page.imageUrl!!, pageHeaders)
    }

    override fun getChapterUrl(chapter: SChapter): String =
        baseUrl + chapter.url.substringBeforeLast("#")

    // ============================= Utilities ==============================

    private fun JsonObject.toRequestBody(): RequestBody {
        return json.encodeToString(this).toRequestBody(JSON_MEDIA_TYPE)
    }

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromStream(it.body.byteStream())
    }

    private fun EntryDto.toSManga(baseUrl: String): SManga = SManga.create().apply {
        title = name
        setUrlWithoutDomain(webUrl)
        thumbnail_url = "$baseUrl/_next/image?url=$thumbUrl&w=256&q=75"
    }

    private fun ChapterDto.toSChapter(url: String): SChapter = SChapter.create().apply {
        name = chapterName
        setUrlWithoutDomain("$url#$idx")
    }

    companion object {
        private const val PAGE_LIMIT = 24
        private val JSON_MEDIA_TYPE = "application/json".toMediaTypeOrNull()

        private val CHAPTER_REGEX by lazy { Regex("""mangaChapters\\\":(.*?\}]),\\\"mangaIdx""") }
    }
}
