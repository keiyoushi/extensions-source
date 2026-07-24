package eu.kanade.tachiyomi.extension.vi.vinahentai

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar

@Source
abstract class VinaHentai : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(3)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/danh-sach/".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "views")
            .build()
        return responseMangaList(client.get(url))
    }

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/danh-sach/".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "updatedAt")
            .build()
        return responseMangaList(client.get(url))
    }

    // ============================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
                .addQueryParameter("q", query)
                .build()
            return responseMangaSearch(client.get(url))
        }

        var genreSlug: String? = null
        var sort = "updatedAt"
        var status = ""

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> genreSlug = filter.selected
                is SortFilter -> sort = filter.toUriPart()
                is StatusFilter -> status = filter.toUriPart()
                else -> {}
            }
        }

        return if (genreSlug != null) {
            val url = "$baseUrl/genres/$genreSlug".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
                .addQueryParameter("sort", sort)
                .apply { if (status.isNotEmpty()) addQueryParameter("status", status) }
                .build()
            responseMangaList(client.get(url))
        } else {
            val url = "$baseUrl/danh-sach".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
                .addQueryParameter("sort", sort)
                .apply { if (status.isNotEmpty()) addQueryParameter("status", status) }
                .build()
            responseMangaList(client.get(url))
        }
    }

    private fun responseMangaList(response: Response): MangasPage {
        val mangas = response.asJsoup()
            .select(".grid a[href*=\"/truyen-hentai/\"]")
            .mapNotNull { mapMangaGrid(it) }
            .distinctBy { it.url }

        val hasNextPage = mangas.size >= MANGA_PER_PAGE

        return MangasPage(mangas, hasNextPage)
    }

    private fun responseMangaSearch(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document
            .select("a.group[href^=\"/truyen-hentai/\"]")
            .mapNotNull { mapMangaSearch(it) }
            .distinctBy { it.url }

        val currentPage = PAGE_REGEX
            .find(document.location())?.groupValues?.get(1)?.toIntOrNull() ?: 1

        val maxPage = document.selectFirst("input[type=number][max]")
            ?.attr("max")
            ?.toIntOrNull() ?: 1

        val hasNextBtn = document.select("a[href*=\"page=${currentPage + 1}\"]").isNotEmpty() ||
            document.select("button[title*=\"${currentPage + 1}\"]").isNotEmpty()

        val hasNextPage = (currentPage < maxPage) || hasNextBtn

        return MangasPage(mangas, hasNextPage)
    }

    private fun mapMangaGrid(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        val titleDiv = element.selectFirst("div.truncate.font-semibold[title]")
        title = titleDiv?.attr("title") ?: titleDiv?.text() ?: ""
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    private fun mapMangaSearch(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        title = element.selectFirst("h2")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    // ============================== Details ===============================

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = SManga.create().apply {
        setUrlWithoutDomain(manga.url)

        title = document.selectFirst("h1")!!.text()

        author = document.select("a[href^=/authors/]")
            .map { it.text() }
            .filter { !it.startsWith("+") }
            .joinToString()
            .ifEmpty { null }

        genre = document.select("a[href^=/genres/]")
            .map { it.text() }
            .filter { !it.startsWith("+") }
            .joinToString()
            .ifEmpty { null }

        thumbnail_url = document.selectFirst("img[alt*=Bìa]")?.absUrl("src")
            ?: document.selectFirst("img[src*=story-images]")?.absUrl("src")

        description = document.selectFirst("#manga-description-section .text-txt-secondary")
            ?.text()

        status = document.body().text().let { bodyText ->
            when {
                bodyText.contains("Đang tiến hành") -> SManga.ONGOING
                bodyText.contains("Đã hoàn thành") -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val detailUrl = if (url.pathSegments.firstOrNull() == "truyen-hentai") {
            url
        } else {
            val chapterDocument = client.get(url).asJsoup()
            if (chapterDocument.selectFirst("a[aria-label=Đọc từ đầu]") == null) return null
            chapterDocument.selectFirst("a[href*=\"/truyen-hentai/\"]")
                ?.absUrl("href")
                ?.toHttpUrl()
                ?: return null
        }
        val slug = detailUrl.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply {
            setUrlWithoutDomain("/truyen-hentai/$slug/")
        }
        return fetchMangaUpdate(manga, emptyList(), true, false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        return SMangaUpdate(
            manga = parseMangaDetails(document, manga),
            chapters = if (fetchChapters) fetchChapters(document) else chapters,
        )
    }

    private fun fetchChapters(document: Document): List<SChapter> = document.select("a.block[href^=/truyen-hentai/]")
        .filter { element ->
            val href = element.attr("href")
            href.count { it == '/' } > 2
        }
        .map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                name = element.selectFirst("span")?.text() ?: element.text()
                date_upload = parseRelativeDate(element.selectFirst("time")?.text())
            }
        }
        .distinctBy { it.url }

    private fun parseRelativeDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L

        val calendar = Calendar.getInstance()
        val number = NUMBER_REGEX.find(dateStr)?.value?.toIntOrNull() ?: return 0L

        when {
            dateStr.contains("giây") -> calendar.add(Calendar.SECOND, -number)
            dateStr.contains("phút") -> calendar.add(Calendar.MINUTE, -number)
            dateStr.contains("giờ") -> calendar.add(Calendar.HOUR_OF_DAY, -number)
            dateStr.contains("ngày") -> calendar.add(Calendar.DAY_OF_MONTH, -number)
            dateStr.contains("tuần") -> calendar.add(Calendar.WEEK_OF_YEAR, -number)
            dateStr.contains("tháng") -> calendar.add(Calendar.MONTH, -number)
            dateStr.contains("năm") -> calendar.add(Calendar.YEAR, -number)
            else -> return 0L
        }

        return calendar.timeInMillis
    }

    // ============================== Pages =================================
    private val imageUrlRegex: Regex
        get() {
            val baseDomain = baseUrl.removePrefix("https://").removePrefix("http://")
            return Regex("""https://vnht\.${Regex.escape(baseDomain)}/manga-images/[^"'\s\\]+""")
        }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val body = client.get(getChapterUrl(chapter)).use { it.body.string() }

        val imageUrls = imageUrlRegex.findAll(body)
            .map { it.value }
            .distinct()
            .toList()

        if (imageUrls.isEmpty()) return emptyList()

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/danh-sach").asJsoup()

        return document.select("a[href*=/genres/]")
            .mapNotNull { element ->
                val slug = element.attr("href")
                    .substringAfter("/genres/")
                    .substringBefore("?")
                    .substringBefore("/")
                val name = element.text().trim()

                if (slug.isNotEmpty() && name.isNotEmpty()) {
                    GenreOption(name = name, slug = slug)
                } else {
                    null
                }
            }
            .distinctBy { it.slug }
            .sortedBy { it.name.lowercase() }
            .toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreOption>>().orEmpty()

        return FilterList(
            buildList {
                if (genres.isNotEmpty()) {
                    add(GenreFilter(genres))
                }
                add(SortFilter())
                add(StatusFilter())
            },
        )
    }

    companion object {
        private const val MANGA_PER_PAGE = 24

        private val NUMBER_REGEX = Regex("""\d+""")
        private val PAGE_REGEX = Regex("""page=(\d+)""")
    }
}
