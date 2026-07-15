package eu.kanade.tachiyomi.extension.es.codearc

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import kotlin.time.Duration.Companion.seconds

@Source
abstract class CodeArc : HttpSource() {
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .connectTimeout(15.seconds)
        .readTimeout(30.seconds)
        .rateLimit(1, 2.seconds) { it.host == baseUrlHost }
        .rateLimit(1, 1.seconds) { it.host == "cdn.codearctraducciones.com" }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val rscHeaders = headersBuilder()
        .add("RSC", "1")
        .build()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/ranking?mode=popular&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("a.group.relative.min-w-0[href]").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.selectFirst("div.truncate.text-base")!!.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
                    ?: element.selectFirst("img")?.attr("srcSet")?.split(",")
                        ?.firstOrNull()?.trim()?.split(" ")?.firstOrNull()
            }
        }

        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val hasNextPage = currentPage < POPULAR_MAX_PAGE && mangas.isNotEmpty() &&
            document.selectFirst("a[aria-label=Pagina siguiente]:not([disabled]), button[aria-label=Pagina siguiente]:not([disabled])") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/list?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("a.group.overflow-hidden[href]").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.selectFirst("div.line-clamp-2")?.text()
                    ?: element.attr("aria-label").takeIf { it.isNotEmpty() }!!
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
                    ?: element.selectFirst("img")?.attr("srcSet")?.split(",")
                        ?.firstOrNull()?.trim()?.split(" ")?.firstOrNull()
            }
        }

        val hasNextPage = mangas.isNotEmpty() &&
            document.selectFirst("a[aria-label=Pagina siguiente]:not([disabled]), button[aria-label=Pagina siguiente]:not([disabled])") != null

        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty() && filters.none { it is UriPartFilter && it.state != 0 } &&
            filters.none { it is GenreGroup && it.state.any { genre -> genre.state } }
        ) {
            val url = "$baseUrl/api/mangas/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("limit", "50")
            return GET(url.build(), headers)
        }

        val url = "$baseUrl/list".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            if (query.isNotEmpty()) {
                addQueryParameter("q", query)
            }
            filters.forEach { filter ->
                when (filter) {
                    is ContentTypeFilter -> {
                        val part = filter.toUriPart()
                        if (part.isNotEmpty()) addQueryParameter("tipo", part)
                    }
                    is FormatFilter -> {
                        val part = filter.toUriPart()
                        if (part != "both") addQueryParameter("formato", part)
                    }
                    is SortFilter -> {
                        val part = filter.toUriPart()
                        if (part != "latest") addQueryParameter("sort", part)
                    }
                    is GenreGroup -> {
                        val genres = filter.state
                            .filter { it.state }
                            .joinToString(",") { it.slug }
                        if (genres.isNotEmpty()) {
                            addQueryParameter("generos", genres)
                        }
                    }
                    else -> {}
                }
            }
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.pathSegments.contains("api")) {
            val result = response.parseAs<SearchResponseDto>()
            val mangas = result.items.map { it.toSManga(baseUrl) }
            return MangasPage(mangas, false)
        }
        return latestUpdatesParse(response)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text().replace("Vista Previa", "")
            description = document.selectFirst("p.whitespace-pre-line")?.text()
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
            genre = document.select("a[href*=/list?generos=]").joinToString { it.text() }

            val htmlArtists = document.select("a[href*=/creador/]").joinToString { it.text() }
            if (htmlArtists.isNotEmpty()) {
                artist = htmlArtists
                author = htmlArtists
            }

            val statusText = document.selectFirst("span.inline-flex:has(span.rounded-full)")
                ?.text()?.lowercase()
            status = when {
                statusText == null -> SManga.UNKNOWN
                statusText.contains("finalizado") -> SManga.COMPLETED
                statusText.contains("publicándose") || statusText.contains("publicandose") -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapterLinks = document.select("a.group.block[href*=/reader/][href*=/cascade]")

        if (chapterLinks.isNotEmpty()) {
            return chapterLinks.map { element ->
                val href = element.absUrl("href")
                val chapterText = element.selectFirst("h3")?.text() ?: ""
                val chapterNum = CHAPTER_NUM_REGEX.find(href)?.groupValues?.get(1)

                SChapter.create().apply {
                    setUrlWithoutDomain(href)
                    name = chapterText.ifEmpty { "Chapter ${chapterNum ?: "1"}" }
                    chapter_number = chapterNum?.toFloatOrNull() ?: 0f
                }
            }
        }

        val singleChapterBtn = document.selectFirst("a[href*=/cascade]:has(span:contains(Leer))")
            ?: document.selectFirst("a[href*=/reader/][href*=/cascade]")

        if (singleChapterBtn != null) {
            return listOf(
                SChapter.create().apply {
                    setUrlWithoutDomain(singleChapterBtn.absUrl("href"))
                    name = "Chapter 1"
                    chapter_number = 1f
                },
            )
        }

        return emptyList()
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, rscHeaders)

    override fun pageListParse(response: Response): List<Page> {
        val readerData = response.extractNextJs<ReaderDto>() ?: return emptyList()
        val pages = readerData.initialPages.toMutableList()
        val pagesFetchUrl = baseUrl.toHttpUrl().resolve(readerData.pagesFetchUrl) ?: return emptyList()

        while (pages.size < readerData.totalPages) {
            val url = pagesFetchUrl.newBuilder()
                .setQueryParameter("offset", pages.size.toString())
                .build()
            val newPages = client.newCall(GET(url, headers)).execute().use { apiResponse ->
                apiResponse.parseAs<ReaderPagesDto>().items
            }
            if (newPages.isEmpty()) break
            pages += newPages
        }

        return pages.mapIndexed { index, page ->
            Page(index, imageUrl = page.imagenUrl)
        }
    }

    override fun getFilterList(): FilterList = getFilters()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private companion object {
        const val POPULAR_MAX_PAGE = 5
        val CHAPTER_NUM_REGEX = """/reader/[^/]+/(\d+)/""".toRegex()
    }
}
