package eu.kanade.tachiyomi.extension.es.codearc

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import kotlin.time.Duration.Companion.seconds

@Source
abstract class CodeArc : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = connectTimeout(15.seconds)
        .readTimeout(30.seconds)
        .rateLimit(1, 2.seconds) { it.host == baseUrl.toHttpUrl().host }
        .rateLimit(1, 1.seconds) { it.host == "cdn.codearctraducciones.com" }
        .addInterceptor(ReaderPageRefresh(::client, ::headers, ::baseUrl))

    private val rscHeaders get() = headers.newBuilder().add("RSC", "1").build()

    override suspend fun getPopularManga(page: Int): MangasPage = popularMangaParse(client.get("$baseUrl/ranking?mode=popular&page=$page"))

    private fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("a.group.relative.min-w-0[href]").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.selectFirst("div.truncate.text-base")!!.text()
                val image = element.selectFirst("img")
                thumbnail_url = image?.absUrl("src")
                    ?: image?.attr("srcSet")?.split(",")
                        ?.firstOrNull()?.trim()?.split(" ")?.firstOrNull()
            }
        }

        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val hasNextPage = currentPage < POPULAR_MAX_PAGE && mangas.isNotEmpty() &&
            document.selectFirst("a[aria-label=Pagina siguiente]:not([disabled]), button[aria-label=Pagina siguiente]:not([disabled])") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = latestUpdatesParse(client.get("$baseUrl/list?page=$page"))

    private fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("a.group.overflow-hidden[href]").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.selectFirst("div.line-clamp-2")?.text()
                    ?: element.attr("aria-label").takeIf { it.isNotEmpty() }!!
                val image = element.selectFirst("img")
                thumbnail_url = image?.absUrl("src")
                    ?: image?.attr("srcSet")?.split(",")
                        ?.firstOrNull()?.trim()?.split(" ")?.firstOrNull()
            }
        }

        val hasNextPage = mangas.isNotEmpty() &&
            document.selectFirst("a[aria-label=Pagina siguiente]:not([disabled]), button[aria-label=Pagina siguiente]:not([disabled])") != null

        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotEmpty() && filters.none { it is UriPartFilter && it.state != 0 } &&
            filters.none { it is GenreGroup && it.state.any { genre -> genre.state } }
        ) {
            val url = "$baseUrl/api/mangas/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("limit", "50")
            return searchMangaParse(client.get(url.build()))
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
        return searchMangaParse(client.get(url.build()))
    }

    private fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.pathSegments.contains("api")) {
            val result = response.parseAs<SearchResponseDto>()
            val mangas = result.items.map { it.toSManga(baseUrl) }
            return MangasPage(mangas, false)
        }
        return latestUpdatesParse(response)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        val slug = when {
            segments.firstOrNull() == "reader" -> segments.getOrNull(1)
            segments.size == 1 -> segments[0]
            else -> null
        }?.takeIf { it.isNotEmpty() } ?: return null
        val mangaUrl = "$baseUrl/$slug"

        return parseMangaDetails(client.get(mangaUrl).asJsoup()).apply {
            setUrlWithoutDomain(mangaUrl)
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = client.get(getMangaUrl(manga)).asJsoup().let { document ->
        SMangaUpdate(
            parseMangaDetails(document),
            parseChapterList(document),
        )
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1")!!.text().replace("Vista Previa", "")
        description = document.selectFirst("p.whitespace-pre-line")?.textOrNull()
        thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
        genre = document.select("a[href*=/list?generos=]").joinToString { it.text() }.ifEmpty { null }

        val htmlArtists = document.select("a[href*=/creador/]").joinToString { it.text() }.ifEmpty { null }
        if (htmlArtists != null) {
            artist = htmlArtists
            author = htmlArtists
        }

        val statusText = document.selectFirst("span.inline-flex:has(span.rounded-full)")
            ?.text()?.lowercase()
        status = when {
            statusText == null -> SManga.UNKNOWN
            statusText.contains("finalizado") -> SManga.COMPLETED
            statusText.contains("publicándose") || statusText.contains("publicandose") ||
                statusText.contains("emisión") || statusText.contains("emision") -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga), rscHeaders)
        .extractNextJs<RelatedResponseDto>()
        ?.items?.map { it.toSManga() }.orEmpty()

    private fun parseChapterList(document: Document): List<SChapter> {
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

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter).toHttpUrl()
        val pages = fetchReaderPages(client, headers, chapterUrl.toString(), readerPagesUrl(chapterUrl))

        return pages.mapIndexed { index, page ->
            Page(
                index,
                imageUrl = page.imagenUrl.toHttpUrl().newBuilder()
                    .setQueryParameter("reader_slug", chapterUrl.pathSegments[1])
                    .setQueryParameter("reader_chapter", chapterUrl.pathSegments[2])
                    .setQueryParameter("reader_page", page.orden.toString())
                    .build()
                    .toString(),
            )
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    private companion object {
        const val POPULAR_MAX_PAGE = 5
        val CHAPTER_NUM_REGEX = """/reader/[^/]+/(\d+)/""".toRegex()
    }
}
