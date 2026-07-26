package eu.kanade.tachiyomi.extension.vi.luvevaland

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
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class LuvEvaLand : KeiSource() {
    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.get("$baseUrl/truyen-tranh")
        val document = response.asJsoup()

        val mangas = document.select("#total-tab-content .comic-item")
            .mapNotNull(::popularMangaFromElement)
            .distinctBy { it.url }

        return MangasPage(mangas, hasNextPage = false)
    }

    private fun popularMangaFromElement(element: Element): SManga? {
        val mangaLinkElement = element.select("a[href*=/truyen-tranh/]")
            .firstOrNull {
                val href = it.absUrl("href")
                href.isNotEmpty() && !chapterUrlRegex.containsMatchIn(href)
            }
            ?: return null

        val mangaTitle = element.selectFirst("a.comic-name")!!.text()

        return SManga.create().apply {
            title = mangaTitle
            setUrlWithoutDomain(mangaLinkElement.absUrl("href"))
            thumbnail_url = normalizeThumbnail(extractImageUrl(element.selectFirst(".comic-img img, img")))
        }
    }

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/danh-sach-chuong-moi-cap-nhat".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        val response = client.get(url)
        val document = response.asJsoup()

        val mangas = document.select(".home__lg-book .book-vertical__item")
            .mapNotNull(::latestMangaFromElement)
            .distinctBy { it.url }

        val hasNextPage = document.selectFirst("ul.pagination a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun latestMangaFromElement(element: Element): SManga? {
        val mangaLinkElement = element.selectFirst(".book__lg-title a[href*=/truyen-tranh/], .book__lg-image a[href*=/truyen-tranh/]")
            ?: return null

        val mangaUrl = mangaLinkElement.absUrl("href")
        if (!mangaPathRegex.containsMatchIn(mangaUrl)) return null

        val mangaTitle = element.selectFirst(".book__lg-title a")!!.text()

        return SManga.create().apply {
            title = mangaTitle
            setUrlWithoutDomain(mangaUrl)
            thumbnail_url = normalizeThumbnail(extractImageUrl(element.selectFirst(".book__lg-image img, img")))
        }
    }

    // ============================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("comic_type", "1")
            if (query.isNotEmpty()) addQueryParameter("s", query)
            filters.firstInstanceOrNull<TagFilter>()?.state
                ?.filter { it.state }
                ?.forEach { addQueryParameter("genres[]", it.id) }
        }.build()
        val response = client.get(url)
        val document = response.asJsoup()
        val mangas = document.select("table.book__list tr.book__list-item")
            .mapNotNull(::searchMangaFromRow)
            .distinctBy { it.url }

        val hasNextPage = document.selectFirst("ul.pagination a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun searchMangaFromRow(element: Element): SManga? {
        val linkElement = element.selectFirst("td.book__list-name a[href], td.book__list-image a[href]") ?: return null
        val mangaUrl = linkElement.absUrl("href")
        if (!mangaPathRegex.containsMatchIn(mangaUrl)) return null

        val mangaTitle = element.selectFirst("td.book__list-name a")!!.text()

        return SManga.create().apply {
            title = mangaTitle
            setUrlWithoutDomain(mangaUrl)
            thumbnail_url = normalizeThumbnail(extractImageUrl(element.selectFirst("td.book__list-image img, img")))
        }
    }

    // ============================== Details ===============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "truyen-tranh") return null

        val slug = url.pathSegments.getOrNull(1) ?: return null
        val manga = SManga.create().apply {
            title = slug
            setUrlWithoutDomain("/truyen-tranh/$slug")
        }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get("$baseUrl${manga.url}")
        val document = response.asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document, manga),
            chapters = parseChapterList(document),
        )
    }

    private fun parseMangaDetails(document: Document, manga: SManga): SManga {
        val detailElement = document.selectFirst(".book__detail-container, .book__detail-contain, .comic-info")
        val titleElement = detailElement?.selectFirst(".book__detail-name, .comic-name-detail, .comic-name")
            ?: document.selectFirst(".book__detail-name, .comic-name-detail, .comic-name")
        val thumbnailElement = detailElement?.selectFirst(".book__detail-image img[alt], .comic-image img[alt], img[alt]")
            ?: document.selectFirst(".book__detail-image img[alt], .comic-image img[alt]")

        return SManga.create().apply {
            setUrlWithoutDomain(manga.url)
            title = titleElement!!.text()
            thumbnail_url = normalizeThumbnail(extractImageUrl(thumbnailElement))
            author = detailElement?.selectFirst(".book__detail-text:matchesOwn((?i)^\\s*Tác giả:) a, .comic-author a")
                ?.text()
                ?: detailElement?.selectFirst(".book__detail-text:matchesOwn((?i)^\\s*Tác giả:), .comic-author")
                    ?.text()
                    ?.substringAfter(": ")
            status = parseStatus(
                detailElement?.selectFirst(".book__detail-text:matchesOwn((?i)^\\s*Tình trạng:), .comic-status-detail, .comic-status")?.text()
                    ?: document.selectFirst(".comic-status-detail, .comic-status")?.text(),
            )
            genre = (detailElement ?: document).select(".book__detail-text:matchesOwn((?i)^\\s*Tag:) a[href*=/the-loai/], a[href*=/the-loai/]")
                .joinToString { it.text() }
                .ifEmpty { null }
            description = parseDescription(document)
        }
    }

    private fun parseDescription(document: Document): String? {
        val introPaneId = document.selectFirst("a[role=tab][href^=#]:matchesOwn((?i)GIỚI THIỆU)")
            ?.attr("href")

        val introElement = introPaneId?.let { document.selectFirst(it) }
            ?: document.selectFirst("#intro-tab-content, #comic-intro, .tab-content .tab-pane.active.in, .tab-content .tab-pane.active")

        return introElement?.text()?.ifEmpty { null }
    }

    private fun parseStatus(statusText: String?): Int {
        val status = statusText?.lowercase() ?: return SManga.UNKNOWN
        return when {
            "đang tiến hành" in status -> SManga.ONGOING
            "hoàn thành" in status || "truyện full" in status -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Chapters ==============================

    private fun parseChapterList(document: Document): List<SChapter> {
        val rowChapters = extractChapterRows(document)
            .mapNotNull(::chapterFromRow)
            .sortedByDescending { it.first }
            .map { it.second }

        return rowChapters
    }

    private fun extractChapterRows(document: Document): List<Element> = document
        .select("table.list-chapter tbody tr, table.list-chapter__container tbody tr, .chapter-list-inner tr, tr.sort-item")
        .ifEmpty { document.select("table tr") }

    private fun chapterFromRow(element: Element): Pair<Int, SChapter>? {
        val chapterNameElement = element.selectFirst("td.list-chapter__name a, td:first-child a") ?: return null

        val chapterLinkElement = element.selectFirst("a[href*=/chap], a[href*=/chuong], a[href*=/chapter]") ?: return null
        val chapterUrl = chapterLinkElement.absUrl("href")
        if (!chapterUrlRegex.containsMatchIn(chapterUrl)) return null

        val chapterName = chapterNameElement.ownText().ifEmpty { chapterNameElement.text() }

        val chapterOrder = element.attr("data-order").toIntOrNull()
            ?: chapterNumberRegex.find(chapterUrl)?.groupValues?.get(1)?.toIntOrNull()
            ?: 0

        val chapterDate = element.selectFirst("td.list-chapter__date, td:last-child")
            ?.text()
            ?.let(::parseDate)
            ?: 0L

        return chapterOrder to SChapter.create().apply {
            name = chapterName
            setUrlWithoutDomain(chapterUrl)
            date_upload = chapterDate
        }
    }

    private fun parseDate(date: String): Long = runCatching {
        LocalDate.parse(date, dateFormat)
            .atStartOfDay(dateZone)
            .toInstant()
            .toEpochMilli()
    }.getOrDefault(0L)

    // ============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get("$baseUrl${chapter.url}")
        val document = response.asJsoup()

        val images = document.select("#view-chapter img, #chapter-content img, .chapter-content img, .reading-content img, .content-chapter img, .box-chapter-content img")
            .map { it.absUrl("data-src").ifEmpty { it.absUrl("src") } }
            .filter { it.isNotBlank() && !it.startsWith("data:image") }

        if (images.isNotEmpty()) {
            return images.mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
        }

        throw Exception("Không tìm thấy hình ảnh cho chương này")
    }

    // ============================== Helpers ===============================

    private fun extractImageUrl(element: Element?): String? {
        if (element == null) return null

        val imageUrl = element.absUrl("data-src")
            .ifEmpty { element.absUrl("src") }

        return imageUrl.ifEmpty { null }
    }

    private fun normalizeThumbnail(url: String?): String? {
        if (url == null) return null
        return url.replace(thumbnailSizeRegex, "")
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/tim-kiem").asJsoup()
        .select("select[name='genres[]'] option[value]")
        .mapNotNull { option ->
            val id = option.attr("value").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val name = option.text().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            GenreOption(name, id)
        }
        .distinctBy { it.id }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreOption>>())

    private val mangaPathRegex = Regex("""/truyen-tranh/""")
    private val chapterUrlRegex = Regex("""/(?:chap|chuong|chapter)""", RegexOption.IGNORE_CASE)
    private val chapterNumberRegex = Regex("""/(?:chap|chuong|chapter)-([0-9]+)""", RegexOption.IGNORE_CASE)
    private val thumbnailSizeRegex = Regex("""-[0-9]+x[0-9]+(?=\.(?:jpe?g|png|webp)$)""")
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    private val dateZone = ZoneId.of("Asia/Ho_Chi_Minh")
}
