package eu.kanade.tachiyomi.extension.en.comicfans

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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class ComicFans : HttpSource() {

    override val name = "Comic Fans"

    override val baseUrl = "https://comicfans.io"
    private val apiUrl = "https://api.comicfans.io/comic-backend/api/v1/content"
    private val cdnUrl = "https://static.comicfans.io"

    override val lang = "en"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private fun apiHeadersBuilder() = headersBuilder().apply {
        add("Accept", "*/*")
        add("Host", apiUrl.toHttpUrl().host)
        add("Origin", baseUrl)
        add("site-domain", "www.${baseUrl.toHttpUrl().host}")
    }

    private val apiHeaders by lazy { apiHeadersBuilder().build() }

    private val json: Json by injectLazy()

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        val body = buildJsonObject {
            put("conditionJson", "{\"title\":\"You may also like\",\"maxSize\":15}")
            put("pageNumber", page)
            put("pageSize", 30)
        }.let(json::encodeToString).toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val popularHeaders = apiHeadersBuilder().apply {
            set("Accept", "application/json")
        }.build()

        return POST("$apiUrl/books/custom/MostPopularLocal#$page", popularHeaders, body)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<ListDataDto<MangaDto>>().data
        val hasNextPage = response.request.url.fragment!!.toInt() < data.totalPages

        return MangasPage(data.list.map { it.toSManga(cdnUrl) }, hasNextPage)
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangaList = document.select(
            "div:has(>.block-title-bar > .title:contains(New Updates))" +
                "> .book-container > .book",
        ).map { element ->
            SManga.create().apply {
                thumbnail_url = element.selectFirst("img")!!.attr("abs:src")
                with(element.selectFirst(".book-name > a")!!) {
                    title = text()
                    setUrlWithoutDomain(attr("abs:href"))
                }
            }
        }

        return MangasPage(mangaList, false)
    }
    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/books".toHttpUrl().newBuilder().apply {
            addQueryParameter("pageNumber", page.toString())
            addQueryParameter("pageSize", "20")
            fragment(page.toString())

            if (query.isNotBlank()) {
                addPathSegment("search")
                addQueryParameter("keyWord", query)
            } else {
                filters.getUriPart<GenreFilter>()?.let {
                    addQueryParameter("genre", it)
                }
                filters.getUriPart<LastUpdateFilter>()?.let {
                    addQueryParameter("withinDay", it)
                }
                filters.getUriPart<StatusFilter>()?.let {
                    addQueryParameter("status", it)
                }
            }
        }.build()

        return GET(url, apiHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage =
        popularMangaParse(response)

    // =============================== Filters ==============================

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Text search ignores filters"),
        Filter.Separator(),
        GenreFilter(),
        LastUpdateFilter(),
        StatusFilter(),
    )

    // =========================== Manga Details ============================

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun mangaDetailsRequest(manga: SManga): Request {
        val bookId = manga.url.substringAfter("/comic/")
            .substringBefore("-")

        return GET("$apiUrl/books/$bookId", apiHeaders)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return response.parseAs<DataDto<MangaDto>>().data.toSManga(cdnUrl)
    }

    // ============================== Chapters ==============================

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override fun chapterListRequest(manga: SManga): Request {
        val bookId = manga.url.substringAfter("/comic/")
            .substringBefore("-")

        return GET("$apiUrl/chapters/page?sortDirection=ASC&bookId=$bookId&pageNumber=1&pageSize=9999", apiHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.parseAs<ListDataDto<ChapterDto>>().data.list.mapIndexed { index, chapterDto ->
            chapterDto.toSChapter(index + 1)
        }.reversed()
    }

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfter("/episode/")
            .substringBefore("-")

        return GET("$apiUrl/chapters/$chapterId", apiHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        return response.parseAs<DataDto<PageDataDto>>().data.comicImageList.map {
            Page(it.sortNum, imageUrl = "$cdnUrl/${it.imageUrl}")
        }
    }

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder().apply {
            add("Accept", "image/avif,image/webp,*/*")
            add("Host", page.imageUrl!!.toHttpUrl().host)
        }.build()

        return GET(page.imageUrl!!, imgHeaders)
    }

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException()

    // ============================= Utilities ==============================

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }
}
