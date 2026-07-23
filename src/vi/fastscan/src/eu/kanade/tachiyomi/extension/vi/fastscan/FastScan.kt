package eu.kanade.tachiyomi.extension.vi.fastscan

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class FastScan : KeiSource() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter().apply { state = 4 }))

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter().apply { state = 0 }))

    // ============================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addPathSegment("tim-kiem")
                addQueryParameter("q", query)
            } else {
                addPathSegment("tim-kiem-nang-cao")

                addQueryParameter("category", filters.firstInstanceOrNull<GenreFilter>()?.selected ?: "")
                addQueryParameter("notcategory", "")
                addQueryParameter("status", filters.firstInstanceOrNull<StatusFilter>()?.value ?: "0")
                addQueryParameter("minchapter", filters.firstInstanceOrNull<MinChapterFilter>()?.value ?: "0")
                addQueryParameter("sort", filters.firstInstanceOrNull<SortFilter>()?.value ?: "0")
            }
            addQueryParameter("page", page.toString())
        }.build()

        return parseMangaListPage(client.get(url))
    }

    private fun parseMangaListPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("ul.list_grid.grid > li").mapNotNull { element ->
            val mangaLink = element.selectFirst(".book_avatar a, .book_name a") ?: return@mapNotNull null
            val mangaTitle = element.selectFirst(".book_name a")?.text() ?: return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(mangaLink.absUrl("href"))
                title = mangaTitle
                thumbnail_url = element.selectFirst("img")?.let { img ->
                    img.absUrl("data-src").ifEmpty { img.absUrl("src") }
                }
            }
        }

        val hasNextPage = document.selectFirst(".page_redirect a:contains(›)") != null

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Details ===============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val detailUrl = if (url.pathSegments.getOrNull(1)?.startsWith("chuong-") == true) {
            client.get(url).asJsoup().select(".breadcrumb a[href]")
                .map { it.absUrl("href").toHttpUrl() }
                .firstOrNull { detailPathRegex.matches(it.encodedPath) }
                ?: return null
        } else {
            url
        }

        if (!detailPathRegex.matches(detailUrl.encodedPath)) return null

        val manga = SManga.create().apply {
            setUrlWithoutDomain(detailUrl.encodedPath)
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
            chapters = parseChapterList(document),
        )
    }

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = SManga.create().apply {
        setUrlWithoutDomain(manga.url)
        title = document.selectFirst(".book_detail .book_other h1")!!.text()
        author = document.selectFirst("P:contains(Tác giả) + p")?.text()
        genre = document.select(".book_other ul.list01 a").joinToString { it.text() }
        description = document.select(".story-detail-info")
            .joinToString("\n\n") { container ->
                val blocks = container.select("p")
                if (blocks.isNotEmpty()) blocks.joinToString("\n\n") { it.wholeText().trim() } else container.wholeText().trim()
            }
        status = parseStatus(document.selectFirst("li.status p.col-xs-9")?.text())
        thumbnail_url = document.selectFirst(".book_info .book_avatar img")?.let { img ->
            img.absUrl("data-src").ifEmpty { img.absUrl("src") }
        }
    }

    private fun parseStatus(statusText: String?): Int = when {
        statusText == null -> SManga.UNKNOWN
        "đang cập nhật" in statusText.lowercase() -> SManga.ONGOING
        "hoàn thành" in statusText.lowercase() -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    private fun parseChapterList(document: Document): List<SChapter> = document.select(".list_chapter .works-chapter-item").map { element ->
        chapterFromElement(element)
    }

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val chapterLink = element.selectFirst(".name-chap a[href]")!!
        setUrlWithoutDomain(chapterLink.absUrl("href"))
        name = chapterLink.text()
        date_upload = parseDate(element.selectFirst(".time-chap")?.text())
    }

    // ============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()

        return document.select("#chapter_content img, .page-chapter img, img.lozad")
            .mapNotNull { element ->
                val url = element.absUrl("data-src").ifEmpty { element.absUrl("src") }
                url.takeIf { it.isNotBlank() && !it.startsWith("data:") }
            }
            .distinct()
            .mapIndexed { index, imageUrl ->
                Page(index, imageUrl = imageUrl)
            }
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get(baseUrl).asJsoup()
        .select("a[href*=/the-loai/]")
        .mapIndexedNotNull { index, element ->
            val name = element.text().takeIf { it.isNotEmpty() } ?: return@mapIndexedNotNull null
            GenreOption((index + 1).toString(), name)
        }
        .distinctBy { it.name }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreOption>>())

    // =============================== Related ==============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val relatedList = document.select("h3")
            .firstOrNull { it.text().contains("liên quan", ignoreCase = true) }
            ?.nextElementSibling()
            ?.selectFirst("ul.list_grid.grid")
            ?: return emptyList()

        return relatedList.select("li").mapNotNull { element ->
            val mangaLink = element.selectFirst(".book_avatar a, .book_name a") ?: return@mapNotNull null
            val mangaTitle = element.selectFirst(".book_name a")?.text() ?: return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(mangaLink.absUrl("href"))
                title = mangaTitle
                thumbnail_url = element.selectFirst("img")?.let { img ->
                    img.absUrl("data-src").ifEmpty { img.absUrl("src") }
                }
            }
        }.distinctBy { it.url }
    }

    private fun parseDate(date: String?): Long {
        if (date == null) return 0L
        return runCatching {
            LocalDate.parse(date, dateFormat)
                .atStartOfDay(dateZone)
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(0L)
    }

    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    private val dateZone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val detailPathRegex = Regex("/[^/]+-\\d+")
}
