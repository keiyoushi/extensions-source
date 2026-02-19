package eu.kanade.tachiyomi.extension.all.manhwa18net

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup

class Manhwa18Net : HttpSource() {

    override val versionId = 2
    override val name = "Manhwa18.Net"
    override val baseUrl = "https://manhwa18.net"
    override val lang = "en"
    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(3)
        .build()

    private fun Response.asJsoup() = Jsoup.parse(body.string())

    private fun extractPageDto(response: Response): PageDto {
        val document = response.asJsoup()
        val app = document.selectFirst("#app")
            ?: throw Exception("Could not find #app element")
        val data = app.attr("data-page")
        if (data.isBlank()) throw Exception("data-page attribute is empty")
        return data.parseAs<PageDto>()
    }

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
        val props = extractPageDto(response).props

        val listing = props.paginate
            ?: props.popularManga
            ?: props.mangas
            ?: props.latestManhwaMain
            ?: throw Exception("No manga listing found in response")

        val mangas = listing.data.map { manga ->
            SManga.create().apply {
                title = manga.name
                url = "/manga/${manga.slug}"
                thumbnail_url = fixImageUrl(manga.coverUrl ?: manga.thumbUrl)
            }
        }

        return MangasPage(mangas, listing.nextPageUrl != null)
    }
    // ============================================================
    // DETAILS
    // ============================================================

    override fun mangaDetailsParse(response: Response): SManga {
        val props = extractPageDto(response).props
        val manga = props.manga ?: throw Exception("Manga details not found")

        return SManga.create().apply {
            title = manga.name

            description = manga.pilot?.let { Jsoup.parse(it).text() }
                ?: manga.description?.let { Jsoup.parse(it).text() }

            thumbnail_url = fixImageUrl(manga.coverUrl ?: manga.thumbUrl)

            genre = manga.genres?.joinToString(", ") { it.name }

            author = manga.artists?.joinToString(", ") { it.name }?.ifEmpty { null }

            artist = author

            status = when (manga.statusId) {
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
        val props = extractPageDto(response).props
        val manga = props.manga ?: throw Exception("Manga not found")
        val chapters = props.chapters ?: throw Exception("Chapters not found")

        return chapters.map { chapter ->
            SChapter.create().apply {
                name = chapter.name
                url = "/manga/${manga.slug}/${chapter.slug}"
            }
        }
    }

    // ============================================================
    // PAGE LIST
    // ============================================================

    override fun pageListParse(response: Response): List<Page> {
        val props = extractPageDto(response).props
        val chapterContent = props.chapterContent
            ?: throw Exception("Chapter content not found")

        val contentDoc = Jsoup.parse(chapterContent)
        val images = contentDoc.select("img")

        return images.mapIndexedNotNull { index, img ->
            val src = img.attr("src")
                .ifBlank { img.attr("data-src") }
                .ifBlank { img.attr("data-lazy-src") }

            fixImageUrl(src)?.let { Page(index, "", it) }
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request = GET(
        page.imageUrl!!,
        headersBuilder()
            .add("Referer", baseUrl)
            .build(),
    )

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
