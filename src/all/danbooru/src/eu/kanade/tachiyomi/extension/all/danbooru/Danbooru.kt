package eu.kanade.tachiyomi.extension.all.danbooru

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class Danbooru : ParsedHttpSource() {
    override val name: String = "Danbooru"
    override val baseUrl: String = "https://danbooru.donmai.us"
    override val lang: String = "all"
    override val supportsLatest: Boolean = true

    override val client: OkHttpClient = network.cloudflareClient
    private val json: Json by injectLazy()

    private val dateFormat: SimpleDateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH)
    }

    override fun popularMangaRequest(page: Int): Request =
        searchMangaRequest(page, "", FilterList())

    override fun popularMangaFromElement(element: Element): SManga =
        searchMangaFromElement(element)

    override fun popularMangaNextPageSelector(): String =
        searchMangaNextPageSelector()

    override fun popularMangaSelector(): String =
        searchMangaSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = Request(
        url = "$baseUrl/pools/gallery".toHttpUrl().newBuilder().run {
            setEncodedQueryParameter("search[category]", "series")

            filters.forEach {
                when (it) {
                    is FilterTags -> if (it.state.isNotBlank()) {
                        addQueryParameter("search[post_tags_match]", it.state)
                    }

                    is FilterDescription -> if (it.state.isNotBlank()) {
                        addQueryParameter("search[description_matches]", it.state)
                    }

                    is FilterIsDeleted -> if (it.state) {
                        addEncodedQueryParameter("search[is_deleted]", "true")
                    }

                    is FilterCategory -> {
                        setEncodedQueryParameter("search[category]", it.selected)
                    }

                    is FilterOrder -> if (it.selected != null) {
                        addEncodedQueryParameter("search[order]", it.selected)
                    }

                    else -> throw IllegalStateException("Unrecognized filter")
                }
            }

            addEncodedQueryParameter("page", page.toString())

            if (query.isNotBlank()) {
                addQueryParameter("search[name_contains]", query)
            }

            build()
        },

        headers = headers,
    )

    override fun searchMangaSelector(): String =
        ".post-preview"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        url = element.selectFirst(".post-preview-link")?.attr("href")!!
        title = element.selectFirst(".desc")?.text() ?: ""

        thumbnail_url = element.selectFirst("source")?.attr("srcset")
            ?.substringAfterLast(',')?.trim()
            ?.substringBeforeLast(' ')?.trimStart()
    }

    override fun searchMangaNextPageSelector(): String =
        "a.paginator-next"

    override fun latestUpdatesRequest(page: Int): Request =
        searchMangaRequest(page, "", FilterList(FilterOrder("created_at")))

    override fun latestUpdatesSelector(): String =
        searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga =
        searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String =
        searchMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        setUrlWithoutDomain(document.location())

        title = document.selectFirst(".pool-category-series, .pool-category-collection")?.text() ?: ""
        description = document.getElementById("description")?.wholeText() ?: ""
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    override fun chapterListRequest(manga: SManga): Request =
        GET("$baseUrl${manga.url}.json?only=id,created_at", headers)

    override fun chapterListParse(response: Response): List<SChapter> = listOf(
        SChapter.create().apply {
            val data = json.decodeFromString<JsonObject>(response.body.string())

            val id = data["id"]!!.jsonPrimitive.content
            val createdAt = data["created_at"]?.jsonPrimitive?.content

            url = "/pools/$id"
            name = "Oneshot"
            date_upload = createdAt?.let(::parseTimestamp) ?: 0
            chapter_number = 0F
        },
    )

    override fun chapterListSelector(): String =
        throw IllegalStateException("Not used")

    override fun chapterFromElement(element: Element): SChapter =
        throw IllegalStateException("Not used")

    override fun pageListRequest(chapter: SChapter): Request =
        GET("$baseUrl${chapter.url}.json?only=post_ids", headers)

    override fun pageListParse(response: Response): List<Page> =
        json.decodeFromString<JsonObject>(response.body.string())
            .get("post_ids")?.jsonArray
            ?.map { it.jsonPrimitive.content }
            ?.mapIndexed { i, id -> Page(index = i, url = "/posts/$id") }
            ?: emptyList()

    override fun pageListParse(document: Document): List<Page> =
        throw IllegalStateException("Not used")

    override fun imageUrlRequest(page: Page): Request =
        GET("$baseUrl${page.url}.json?only=file_url", headers)

    override fun imageUrlParse(response: Response): String =
        json.decodeFromString<JsonObject>(response.body.string())
            .get("file_url")!!.jsonPrimitive.content

    override fun imageUrlParse(document: Document): String =
        throw IllegalStateException("Not used")

    override fun getChapterUrl(chapter: SChapter): String =
        baseUrl + chapter.url

    override fun getFilterList() = FilterList(
        listOf(
            FilterDescription(),
            FilterTags(),
            FilterIsDeleted(),
            FilterCategory(),
            FilterOrder(),
        ),
    )

    private fun parseTimestamp(string: String): Long? =
        runCatching { dateFormat.parse(string)?.time!! }.getOrNull()
}
