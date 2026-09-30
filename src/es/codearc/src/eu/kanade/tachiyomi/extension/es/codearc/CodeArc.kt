package eu.kanade.tachiyomi.extension.es.codearc

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Duration.Companion.seconds

@Source
abstract class CodeArc : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = connectTimeout(15.seconds)
        .readTimeout(30.seconds)
        .addInterceptor(ReaderPageRefresh(::client, ::headers, ::baseUrl))

    private val rscHeaders get() = headers.newBuilder().add("RSC", "1").build()

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/ranking".toHttpUrl().newBuilder()
            .addQueryParameter("mode", "popular")
            .addQueryParameter("page", page.toString())
            .build()
        val document = client.get(url).asJsoup()
        return popularMangaParse(document, page)
    }

    private fun popularMangaParse(document: Document, page: Int): MangasPage {
        val mangas = document.select("a.group.relative.min-w-0[href]").map { it.mangaFromElement("div.truncate.text-base") }
        // the site repeats page 5 indefinitely, keeping the next link enabled
        return MangasPage(mangas, page < 5 && document.hasNextPage())
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/list".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        val document = client.get(url).asJsoup()
        return parseMangasPage(document)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genreFilter = filters.firstInstanceOrNull<GenreGroup>()
        val uriFilterSelected = filters.any { it is UriPartFilter && it.state != 0 }
        val genreSelected = genreFilter?.state?.any { it.state } == true
        if (query.isNotEmpty() && !uriFilterSelected && !genreSelected) {
            val searchUrl = "$baseUrl/api/mangas/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("limit", "50")
                .build()
            val mangas = client.get(searchUrl).parseAs<SearchResponseDto>().items.map { it.toSManga(baseUrl) }
            return MangasPage(mangas, false)
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
        val document = client.get(url.build()).asJsoup()
        return parseMangasPage(document)
    }

    private fun parseMangasPage(document: Document): MangasPage {
        val mangas = document.select("a.group.overflow-hidden[href]").map { it.mangaFromElement("div.line-clamp-2") }
        return MangasPage(mangas, document.hasNextPage())
    }

    private fun Element.mangaFromElement(titleSelector: String) = SManga.create().apply {
        setUrlWithoutDomain(absUrl("href"))
        title = selectFirst(titleSelector)!!.text()
        thumbnail_url = selectFirst("img")?.absUrl("src")
    }

    private fun Document.hasNextPage() = selectFirst("*[aria-label=Pagina siguiente]:not([disabled])") != null

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        val slug = when {
            segments.firstOrNull() == "reader" -> segments.getOrNull(1)
            segments.size == 1 -> segments[0]
            else -> null
        }?.takeIf { it.isNotEmpty() } ?: return null
        val mangaUrl = "$baseUrl/$slug"

        return client.get(mangaUrl, rscHeaders).extractNextJs<DetailsResponseDto>()?.toSManga()?.apply {
            setUrlWithoutDomain(mangaUrl)
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = client.get(getMangaUrl(manga), rscHeaders).extractNextJs<DetailsResponseDto>()!!.let { details ->
        SMangaUpdate(details.toSManga(), details.toSChapterList())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter).toHttpUrl()
        val pages = fetchReaderPages(client, headers, chapterUrl.toString(), readerPagesUrl(chapterUrl))

        return pages.mapIndexed { index, page ->
            Page(
                index,
                imageUrl = page.imagenUrl.toHttpUrl().newBuilder()
                    .addQueryParameter("reader_slug", chapterUrl.pathSegments[1])
                    .addQueryParameter("reader_chapter", chapterUrl.pathSegments[2])
                    .addQueryParameter("reader_page", page.orden.toString())
                    .build()
                    .toString(),
            )
        }
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga), rscHeaders)
        .extractNextJs<List<RelatedItemDto>>()
        ?.map { it.toSManga() }
        .orEmpty()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()
}
