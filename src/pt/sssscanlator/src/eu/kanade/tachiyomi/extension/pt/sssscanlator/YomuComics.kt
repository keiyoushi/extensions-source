package eu.kanade.tachiyomi.extension.pt.sssscanlator

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class YomuComics : HttpSource() {

    override val name = "Yomu Comics"

    override val baseUrl = "https://yomu.com.br"

    override val lang = "pt-BR"

    override val supportsLatest = true

    // SSSScanlator
    override val id = 1497838059713668619

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("x-yomu-web", "true")

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/api/library".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("sort", "popular")
            .addQueryParameter("type", DEFAULT_TYPE)
            .build()

        return GET(url, bibliotecaHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseLibraryResponse(response)

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/api/library".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("sort", "recent")
            .addQueryParameter("type", DEFAULT_TYPE)
            .build()

        return GET(url, bibliotecaHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseLibraryResponse(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.selectedValue.orEmpty()
        val type = filters.firstInstanceOrNull<TypeFilter>()?.selectedValue ?: DEFAULT_TYPE
        val status = filters.firstInstanceOrNull<StatusFilter>()?.selectedValue ?: DEFAULT_STATUS
        val sort = filters.firstInstanceOrNull<SortFilter>()?.selectedValue ?: DEFAULT_SORT

        val url = "$baseUrl/api/library".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", PAGE_SIZE.toString())
            addQueryParameter("sort", sort)
            addQueryParameter("type", type)

            if (genre.isNotBlank()) {
                addQueryParameter("genre", genre)
            }

            if (status != DEFAULT_STATUS) {
                addQueryParameter("status", status)
            }

            if (query.isNotBlank()) {
                addQueryParameter("search", query)
            }
        }.build()

        return GET(url, bibliotecaHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseLibraryResponse(response)

    // Details

    override fun mangaDetailsParse(response: Response): SManga = parseSeriesPage(response).manga

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url.substringBefore('?')

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> = parseSeriesPage(response).chapters

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = resolveChapterId(chapter)
        val requestHeaders = headers.newBuilder()
            .set("Referer", getChapterUrl(chapter))
            .build()

        return GET("$baseUrl/api/chapters?id=$chapterId", requestHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val pages = response.parseAs<ChapterPagesResponseDto>()
            .chapter
            .content
            .filter(String::isNotBlank)

        if (pages.isEmpty()) {
            throw IllegalStateException("Nenhuma pagina encontrada para este capitulo")
        }

        return pages.mapIndexed { index, imageUrl ->
            val proxyUrl = "$baseUrl/api/proxy-image".toHttpUrl().newBuilder()
                .addQueryParameter("url", imageUrl)
                .toString()

            Page(index, imageUrl = proxyUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Filters

    override fun getFilterList() = FilterList(
        GenreFilter(),
        TypeFilter(),
        StatusFilter(),
        SortFilter(),
    )

    // Utils

    private fun parseLibraryResponse(response: Response): MangasPage {
        val result = response.parseAs<LibraryResponseDto>()
        val mangas = result.data.map(LibraryMangaDto::toSManga)
        val hasNextPage = result.pagination.page < result.pagination.totalPages
        return MangasPage(mangas, hasNextPage)
    }

    private fun parseSeriesPage(response: Response): SeriesPageData {
        val mangaSlug = response.request.url.pathSegments.lastOrNull().orEmpty()
        val document = response.asJsoup()
        val payload = extractSeriesPayload(document, mangaSlug)

        val titleElement = document.selectFirst("h1")
        val title = titleElement!!.text()
        val badgeTexts = extractBadgeTexts(titleElement)
        val statusText = badgeTexts.firstOrNull(::isStatusBadge)
        val genres = badgeTexts.filterNot(::isStatusBadge)

        val manga = SManga.create().apply {
            this.title = title
            thumbnail_url = payload.coverImage?.takeUnless(String::isBlank)
            description = payload.description?.takeUnless(String::isBlank)
            author = payload.author?.takeUnless(String::isBlank)
            artist = payload.artist?.takeUnless(String::isBlank)
            genre = genres.joinToString().takeUnless(String::isBlank)
            status = parseStatus(statusText)
            url = "/obra/$mangaSlug"
        }

        val chapters = payload.chapters.map { chapter ->
            chapter.toSChapter(mangaSlug)
        }

        return SeriesPageData(manga, chapters)
    }

    private fun buildChapterHttpUrl(chapterUrl: String) = when {
        chapterUrl.startsWith("http") -> chapterUrl.toHttpUrl()
        else -> "$baseUrl$chapterUrl".toHttpUrl()
    }

    private fun resolveChapterId(chapter: SChapter): String {
        val chapterUrl = buildChapterHttpUrl(chapter.url)
        chapterUrl.queryParameter("chapterId")?.let { return it }

        val pathSegments = chapterUrl.pathSegments
        val readerSegmentIndex = pathSegments.indexOf("ler")
        if (readerSegmentIndex == -1 || pathSegments.size <= readerSegmentIndex + 2) {
            throw IllegalStateException("chapterId nao encontrado na URL do capitulo")
        }

        val mangaSlug = pathSegments[readerSegmentIndex + 1]
        val chapterNumberLabel = pathSegments[readerSegmentIndex + 2]
        val request = GET("$baseUrl/obra/$mangaSlug", headers)

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Falha ao recarregar a obra para resolver chapterId")
            }

            val payload = extractSeriesPayload(response.asJsoup(), mangaSlug)

            return payload.chapters
                .firstOrNull { it.number.toChapterNumberString() == chapterNumberLabel }
                ?.chapterId
                ?: throw IllegalStateException("chapterId nao encontrado para $mangaSlug/$chapterNumberLabel")
        }
    }

    private data class SeriesPageData(
        val manga: SManga,
        val chapters: List<SChapter>,
    )

    private companion object {
        const val PAGE_SIZE = 20
        const val DEFAULT_TYPE = "all"
        const val DEFAULT_STATUS = "all"
        const val DEFAULT_SORT = "popular"
    }

    private val bibliotecaHeaders
        get() = headers.newBuilder()
            .set("Referer", "$baseUrl/biblioteca")
            .build()
}
