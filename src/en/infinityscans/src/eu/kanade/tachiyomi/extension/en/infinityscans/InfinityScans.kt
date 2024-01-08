package eu.kanade.tachiyomi.extension.en.infinityscans

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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.io.IOException

class InfinityScans : HttpSource() {

    override val name = "InfinityScans"

    override val baseUrl = "https://infinityscans.xyz"
    private val cdnHost = "cdn.${baseUrl.toHttpUrl().host}"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.code == 401) {
                response.close()
                throw IOException("Solve Captcha in WebView")
            }
            response
        }
        .rateLimit(1)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val json: Json by injectLazy()

    // Popular

    override fun popularMangaRequest(page: Int): Request =
        searchMangaRequest(page, "", FilterList(SortFilter("popularity")))

    override fun popularMangaParse(response: Response): MangasPage =
        searchMangaParse(response)

    // Latest

    override fun latestUpdatesRequest(page: Int): Request =
        searchMangaRequest(page, "", FilterList(SortFilter("latest")))

    override fun latestUpdatesParse(response: Response): MangasPage =
        searchMangaParse(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val searchHeaders = headersBuilder().apply {
            add("X-Requested-With", "XMLHttpRequest")
        }.build()

        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("ajax/comics")
            .addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> {
                    url.addQueryParameter("sort", filter.selected)
                }
                is GenreFilter -> {
                    filter.checked?.also {
                        url.addQueryParameter("genre", it.joinToString("|"))
                    }
                }
                is AuthorFilter -> {
                    filter.checked?.also {
                        url.addQueryParameter("author", it.joinToString("|"))
                    }
                }
                is StatusFilter -> {
                    filter.checked?.also {
                        url.addQueryParameter("status", it.joinToString("|"))
                    }
                }
                else -> { /* Do Nothing */ }
            }
        }

        if (query.isNotBlank()) url.addQueryParameter("title", query)

        return GET(url.build(), searchHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val page = response.request.url.queryParameter("page")!!.toInt()
        val data = response.parseAs<ResponseDto<SearchResultDto>>().result
        runCatching { updateFilters(data) }

        val entries = data.comics
            .map { it.toSManga(cdnHost) }

        return MangasPage(entries, page < data.pages)
    }

    // Filters

    private fun updateFilters(data: SearchResultDto) {
        data.genres?.also { genreDto ->
            genreList = genreDto.map { Pair(it.title, it.id.toString()) }
        }

        data.authors?.also { authorDto ->
            authorList = authorDto.map { Pair(it.title, it.id.toString()) }
        }

        data.statuses?.also { status ->
            statusList = status.map { Pair(it, it) }
        }
    }

    private var genreList: List<Pair<String, String>> = emptyList()
    private var authorList: List<Pair<String, String>> = emptyList()
    private var statusList: List<Pair<String, String>> = emptyList()

    override fun getFilterList(): FilterList {
        val filters: MutableList<Filter<*>> = mutableListOf(
            SortFilter(),
        )

        if (genreList.isNotEmpty() || authorList.isNotEmpty() || statusList.isNotEmpty()) {
            if (genreList.isNotEmpty()) filters += listOf(GenreFilter("Genres", genreList))
            if (authorList.isNotEmpty()) filters += listOf(AuthorFilter("Authors", authorList))
            if (statusList.isNotEmpty()) filters += listOf(StatusFilter("Statuses", statusList))
        } else {
            filters += listOf(
                Filter.Separator(),
                Filter.Header("Press 'reset' to attempt to show additional filters"),
            )
        }

        return FilterList(filters)
    }

    // Details

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.use { it.asJsoup() }

        val desc = document.select("div.s1:has(>h2:contains(Summary)) p")
            .text()
            .split("</br>")
            .joinToString("\n", transform = String::trim)
            .trim()

        return SManga.create().apply {
            document.selectFirst("div.info")!!.also { details ->
                description = desc
                author = details.getLinks("Authors")
                genre = details.getLinks("Genres")
                status = details.getInfo("Status").parseStatus()

                details.getInfo("Alternative Title")?.let {
                    description = "$desc\n\nAlternative Title: $it"
                }
            }
        }
    }

    // From mangathemesia
    private fun String?.parseStatus(): Int = when {
        this == null -> SManga.UNKNOWN
        listOf("ongoing", "publishing").any { this.contains(it, ignoreCase = true) } -> SManga.ONGOING
        this.contains("hiatus", ignoreCase = true) -> SManga.ON_HIATUS
        this.contains("completed", ignoreCase = true) -> SManga.COMPLETED
        listOf("dropped", "cancelled").any { this.contains(it, ignoreCase = true) } -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun Element.getInfo(name: String): String? =
        selectFirst("div:has(>b:matches($name:))")?.ownText()

    private fun Element.getLinks(name: String): String? =
        select("div:has(>b:matches($name:)) a")
            .joinToString(", ", transform = Element::text).trim()
            .takeIf { it.isNotBlank() }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val url = response.request.url
        val slug = url.pathSegments.take(3).joinToString("/", prefix = "/")

        // Create POST request
        val id = url.pathSegments[1]
        val form = FormBody.Builder().apply {
            add("comic_id", id)
        }.build()

        val chapterHeaders = headersBuilder().apply {
            add("Accept", "*/*")
            add("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            add("Host", baseUrl.toHttpUrl().host)
            add("Origin", baseUrl)
            set("Referer", url.toString())
            add("X-Requested-With", "XMLHttpRequest")
        }.build()

        val chapterListData = client.newCall(
            POST("$baseUrl/ajax/chapters", chapterHeaders, form),
        ).execute().parseAs<ResponseDto<ChapterDataDto>>()

        return chapterListData.result.chapters.map {
            it.toSChapter(slug)
        }
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val url = response.request.url
        val boundary = buildString {
            append((1..9).random())
            repeat(28) {
                append((0..9).random())
            }
        }

        // Create POST request

        val form = MultipartBody.Builder("-----------------------------$boundary").apply {
            setType(MultipartBody.FORM)
            addPart(
                Headers.headersOf("Content-Disposition", "form-data; name=\"comic_id\""),
                url.pathSegments[1].toRequestBody(null),
            )
            addPart(
                Headers.headersOf("Content-Disposition", "form-data; name=\"chapter_id\""),
                url.pathSegments[4].toRequestBody(null),
            )
        }.build()

        val pageListHeaders = headersBuilder().apply {
            add("Accept", "*/*")
            add("Host", url.host)
            add("Origin", baseUrl)
            set("Referer", url.toString())
            add("X-Requested-With", "XMLHttpRequest")
        }.build()

        val pageListData = client.newCall(
            POST("$baseUrl/ajax/images", pageListHeaders, form),
        ).execute().parseAs<ResponseDto<PageDataDto>>()

        return pageListData.result.images.mapIndexed { index, p ->
            Page(index, url.toString(), p.link)
        }
    }

    // Image

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used.")

    override fun imageRequest(page: Page): Request {
        val pageHeaders = headersBuilder().apply {
            add("Accept", "image/avif,image/webp,*/*")
            add("Host", page.imageUrl!!.toHttpUrl().host)
        }.build()

        return GET(page.imageUrl!!, pageHeaders)
    }

    // Utilities

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromStream(it.body.byteStream())
    }
}
