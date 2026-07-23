package eu.kanade.tachiyomi.extension.vi.meosua

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
abstract class MeoSua : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(3)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/xem-nhieu-nhat/".toHttpUrl().newBuilder()
            .addQueryParameter("trang", page.toString())
            .build()
        return parseMangaList(client.get(url))
    }

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/truyen-moi-cap-nhat/".toHttpUrl().newBuilder()
            .addQueryParameter("trang", page.toString())
            .build()
        return parseMangaList(client.get(url))
    }

    // ============================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/".toHttpUrl().newBuilder()
                .addQueryParameter("s", query)
                .build()
            return parseSearchManga(client.get(url))
        }

        val genrePath = filters.firstInstanceOrNull<GenreFilter>()?.selectedPath()
        if (genrePath != null) {
            val url = "$baseUrl$genrePath".toHttpUrl().newBuilder()
                .addQueryParameter("trang", page.toString())
                .build()
            return parseMangaList(client.get(url))
        }

        return getLatestUpdates(page)
    }

    private fun parseSearchManga(response: Response): MangasPage {
        val mangas = response.asJsoup()
            .select("article.uk-panel.uk-margin-small-bottom:has(h3 a[href*=\"/truyen/\"])")
            .map(::mangaFromElement)
            .distinctBy { it.url }

        return MangasPage(mangas, hasNextPage = false)
    }

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("article.uk-panel.uk-margin-small-bottom:has(h3 a[href*=\"/truyen/\"])")
            .map(::mangaFromElement)
            .distinctBy { it.url }
        val hasNextPage = document.selectFirst(".uk-pagination [uk-pagination-next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga {
        val titleLink = element.selectFirst("h3 a[href*=\"/truyen/\"]")!!
        val mangaUrl = titleLink.absUrl("href").substringBefore("?")

        return SManga.create().apply {
            title = titleLink.text()
            setUrlWithoutDomain(mangaUrl)
            thumbnail_url = element.selectFirst("img")?.let(::imageUrlFromElement)
        }
    }

    // ============================== Details ===============================

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = SManga.create().apply {
        setUrlWithoutDomain(manga.url)
        title = document.selectFirst(
            "h2#category-title, section#single-block h2, #single-block h2.uk-margin-remove-top, h2.uk-margin-remove-top",
        )!!.text()
        thumbnail_url = document.selectFirst(".single-thumb img")?.absUrl("src")
        status = document.selectFirst(".tab-story [uk-icon=\"icon: file-edit\"]")
            ?.parent()
            ?.text()
            ?.let(::parseStatus)
            ?: SManga.UNKNOWN
        genre = document.select(".tab-story a[href*=\"/the-loai/\"]")
            .joinToString { it.text() }
            .ifEmpty { null }
        description = document.selectFirst(".tab-story .hide-long-text p")
            ?.text()
            ?.ifEmpty {
                document.selectFirst(".tab-story h3:contains(Tóm tắt) + p")
                    ?.text()
            }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val detailUrl = if (url.pathSegments.firstOrNull() == "truyen") {
            url
        } else {
            val chapterDocument = client.get(url).asJsoup()
            if (chapterDocument.selectFirst("#view-chapter") == null) return null
            chapterDocument.selectFirst("a[href*=\"/truyen/\"]")
                ?.absUrl("href")
                ?.toHttpUrl()
                ?: return null
        }
        val slug = detailUrl.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply {
            setUrlWithoutDomain("/truyen/$slug/")
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
            chapters = if (fetchChapters) fetchChapters(manga, document) else chapters,
        )
    }

    private suspend fun fetchChapters(manga: SManga, initialDocument: Document): List<SChapter> {
        val allChapters = mutableListOf<SChapter>()

        allChapters += initialDocument
            .select("#chapter-list-tab .chapter-item, .tab-story .chapter-list .chapter-item")
            .mapNotNull(::chapterFromElement)

        val maxPage = initialDocument.select("#chapter-list-tab .uk-pagination a[href*=\"?trang=\"]")
            .mapNotNull {
                chapterPageRegex.find(it.absUrl("href"))
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
            }
            .maxOrNull()
            ?: 1

        for (page in 2..maxPage) {
            val pageUrl = getMangaUrl(manga).toHttpUrl().newBuilder()
                .setQueryParameter("trang", page.toString())
                .build()
            val pageDocument = client.get(pageUrl).asJsoup()
            allChapters += pageDocument
                .select("#chapter-list-tab .chapter-item, .tab-story .chapter-list .chapter-item")
                .mapNotNull(::chapterFromElement)
        }

        return allChapters.distinctBy { it.url }
    }

    private fun chapterFromElement(element: Element): SChapter? {
        val linkElement = element.selectFirst("a.uk-link-toggle") ?: return null
        val chapterUrl = linkElement.absUrl("href")
        if (chapterUrl.isBlank()) return null

        val chapterNameRaw = linkElement.selectFirst("h4")?.text() ?: return null
        val chapterName = chapterNameRegex.find(chapterNameRaw)
            ?.value
            ?.replace(chapterWordRegex, "Chap")
            ?.replace(multiSpaceRegex, " ")
            ?: chapterNameRaw

        val isLocked = element.selectFirst("[uk-icon=\"icon: lock\"], .uk-text-danger[uk-icon]") != null
        val chapterDate = element.selectFirst(".uk-article-meta [uk-icon=\"icon: calendar\"] + span")?.text()

        return SChapter.create().apply {
            setUrlWithoutDomain(chapterUrl)
            name = if (isLocked) "🔒 $chapterName" else chapterName
            date_upload = parseDate(chapterDate)
        }
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

    private fun parseStatus(statusText: String): Int {
        val normalizedStatus = statusText.lowercase()
        return when {
            "trọn bộ" in normalizedStatus -> SManga.COMPLETED
            "đang tiến hành" in normalizedStatus -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()

        if (document.selectFirst("#view-chapter .lock-card, #view-chapter #unlock-chapter, #view-chapter #xu-lock") != null) {
            throw Exception(lockedChapterMessage)
        }

        val imageUrls = document.select("#view-chapter .chapter-content img")
            .ifEmpty { document.select(".chapter-content img, .view-comic img") }
            .mapNotNull(::imageUrlFromElement)
            .filterNot { placeholderImageRegex.containsMatchIn(it) }

        if (imageUrls.isEmpty()) return emptyList()

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    private fun imageUrlFromElement(element: Element): String? = element.absUrl("data-src")
        .ifEmpty { element.absUrl("data-lazy-src") }
        .ifEmpty { element.absUrl("src") }
        .ifEmpty { null }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/the-loai/").asJsoup()
        .select("a.uk-button[href*=\"/the-loai/\"]:not(.uk-disabled)")
        .map { link ->
            GenreOption(
                name = link.text(),
                path = link.absUrl("href").toHttpUrl().encodedPath,
            )
        }
        .distinctBy { it.path }
        .sortedBy { it.name }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreOption>>().orEmpty()
        return FilterList(buildList { if (genres.isNotEmpty()) add(GenreFilter(genres)) })
    }

    // =============================== Related ==============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val relatedSlider = document.select("h3")
            .firstOrNull { it.text().equals("Truyện khác của nhóm", ignoreCase = true) }
            ?.nextElementSibling()
            ?: return emptyList()

        return relatedSlider.select("a.uk-link-toggle[href*=\"/truyen/\"]").mapNotNull { link ->
            val title = link.selectFirst("h3")?.text()
                ?: link.selectFirst("img[alt]")?.attr("alt")?.removePrefix("Ảnh truyện ")
                ?: return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                this.title = title
                thumbnail_url = link.selectFirst("img")?.let(::imageUrlFromElement)
            }
        }.distinctBy { it.url }
    }

    private val lockedChapterMessage =
        "Vui lòng đăng nhập vào tài khoản phù hợp bằng webview để xem chương này"

    private val chapterNameRegex = Regex("chap\\s*\\d+(?:\\.\\d+)?", RegexOption.IGNORE_CASE)
    private val chapterWordRegex = Regex("chap", RegexOption.IGNORE_CASE)
    private val chapterPageRegex = Regex("[?&]trang=(\\d+)")
    private val multiSpaceRegex = Regex("\\s+")
    private val placeholderImageRegex = Regex("/wp-content/uploads/\\d{4}/\\d{2}/(?:0|999)\\.webp$", RegexOption.IGNORE_CASE)
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    private val dateZone = ZoneId.of("Asia/Ho_Chi_Minh")
}
