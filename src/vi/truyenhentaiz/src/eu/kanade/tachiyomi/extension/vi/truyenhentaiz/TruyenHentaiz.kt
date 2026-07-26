package eu.kanade.tachiyomi.extension.vi.truyenhentaiz

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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class TruyenHentaiz : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaPage(client.get(buildPagedUrl("/xem-nhieu-nhat", page)))

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaPage(client.get(buildPagedUrl("/moi-cap-nhat", page)))

    // ============================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            baseUrl.toHttpUrl().newBuilder().apply {
                if (page > 1) {
                    addPathSegment("page")
                    addPathSegment(page.toString())
                }
                addQueryParameter("s", query)
            }.build()
        } else {
            val genreSlug = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()
                ?: return getPopularManga(page)
            buildPagedUrl("/category/$genreSlug", page).toHttpUrl()
        }

        return parseMangaPage(client.get(url))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val detailPath = when {
            url.pathSegments.size == 1 && url.pathSegments[0].endsWith(".html") -> url.encodedPath
            url.pathSegments.size == 2 &&
                url.pathSegments[1].startsWith("chapter-") &&
                url.pathSegments[1].endsWith(".html") -> "/${url.encodedPathSegments[0]}.html"
            else -> return null
        }

        val manga = SManga.create().apply {
            setUrlWithoutDomain(detailPath)
        }
        return fetchMangaUpdate(manga, emptyList(), true, false).manga
    }

    private fun parseMangaPage(response: Response): MangasPage {
        val currentPage = parseCurrentPage(response.request.url)
        val document = response.asJsoup()
        val mangas = document.select("section.container-box-manga .card.card-manga")
            .map(::mangaFromElement)
        val hasNextPage = document.selectFirst(".pagination a.page-link[data-page=${currentPage + 1}]") != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val linkElement = element.selectFirst("div.card-manga-body > a[href]")!!
        title = linkElement.selectFirst("h2.card-manga-title")!!.text()
        setUrlWithoutDomain(linkElement.absUrl("href"))
        thumbnail_url = element.selectFirst("img.card-img-top")?.extractImageUrl()
    }

    private fun buildPagedUrl(path: String, page: Int): String = if (page > 1) {
        "$baseUrl$path/page/$page"
    } else {
        "$baseUrl$path"
    }

    private fun parseCurrentPage(url: HttpUrl): Int {
        val pageIndex = url.pathSegments.indexOf("page")
        return url.pathSegments.getOrNull(pageIndex + 1)?.toIntOrNull() ?: 1
    }

    // ============================== Details ===============================

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
        val mangaInfo = document.selectFirst(".card.mb-3 .manga-info")
        title = document.selectFirst(".card.mb-3 h3.card-title, .pagetitle h1")!!.text()
        thumbnail_url = document.selectFirst(".card.mb-3 img.single-thumbnail, .card.mb-3 img")?.extractImageUrl()
        status = parseStatus(mangaInfo?.selectFirst("span:contains(Status:) strong")?.text())
        genre = mangaInfo?.select(".categories a")
            ?.joinToString { it.text() }
            ?.ifEmpty { null }
        description = document.selectFirst(".card.mb-3 p.desc")
            ?.text()
            ?.ifEmpty { null }
    }

    private fun parseStatus(statusText: String?): Int {
        val normalizedStatus = statusText?.lowercase(Locale.ROOT)
        return when {
            normalizedStatus == null -> SManga.UNKNOWN
            "đang tiến hành" in normalizedStatus || "đang cập nhật" in normalizedStatus -> SManga.ONGOING
            "hoàn thành" in normalizedStatus -> SManga.COMPLETED
            "tạm ngưng" in normalizedStatus || "tạm dừng" in normalizedStatus -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private fun parseChapterList(document: Document): List<SChapter> = document
        .select("div.card:has(h2.card-title:contains(Chapters)) li.list-group-item:has(a[href])")
        .ifEmpty { document.select("li.list-group-item:has(a[href*=/chapter-])") }
        .map { element ->
            val chapterLink = element.selectFirst("a[href]")!!
            SChapter.create().apply {
                name = chapterLink.selectFirst("span.fw-bold")?.text() ?: chapterLink.text()
                setUrlWithoutDomain(chapterLink.absUrl("href"))
                date_upload = parseChapterDate(element.selectFirst("em")?.text())
            }
        }

    private fun parseChapterDate(dateText: String?): Long {
        if (dateText.isNullOrBlank()) return 0L

        val normalizedDate = multipleSpacesRegex.replace(dateText, " ")
        val relativeDate = parseRelativeDate(normalizedDate.lowercase(Locale.ROOT))
        if (relativeDate != 0L) return relativeDate

        return runCatching {
            LocalDateTime.parse(normalizedDate, chapterDateTimeFormat)
                .atZone(vietnamZone)
                .toInstant()
                .toEpochMilli()
        }.recoverCatching {
            LocalDate.parse(normalizedDate, chapterDateFormat)
                .atStartOfDay(vietnamZone)
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(0L)
    }

    private fun parseRelativeDate(dateText: String): Long {
        if (dateText == "mới" || "vừa xong" in dateText) return Clock.System.now().toEpochMilliseconds()

        val amount = relativeDateNumberRegex.find(dateText)?.value?.toIntOrNull() ?: return 0L
        val duration = when {
            "giây" in dateText -> amount.seconds
            "phút" in dateText -> amount.minutes
            "giờ" in dateText -> amount.hours
            "ngày" in dateText -> amount.days
            "tuần" in dateText -> (amount * 7).days
            "tháng" in dateText -> (amount * 30).days
            "năm" in dateText -> (amount * 365).days
            else -> return 0L
        }

        return (Clock.System.now() - duration).toEpochMilliseconds()
    }

    // ============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val imageUrls = document.select("#chapter-content img[src], #chapter-content img[data-src]")
            .mapNotNull { it.extractImageUrl() }
            .filterNot { it.startsWith("data:") }
            .filterNot(::isChapterBanner)
            .ifEmpty {
                document.select("#chapter-content img, .chapter-content img")
                    .mapNotNull { it.extractImageUrl() }
                    .filterNot { it.startsWith("data:") }
                    .filterNot(::isChapterBanner)
            }
            .distinct()

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    private fun isChapterBanner(imageUrl: String): Boolean = imageUrl.toHttpUrlOrNull()?.pathSegments?.lastOrNull() == "bn.png"

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get(baseUrl).asJsoup()
        .select("#sidebar > ul.sidebar-nav > li.nav-item:has(> a:contains(Thể loại)) a[href*=/category/]")
        .mapNotNull { link ->
            val name = link.text().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val slug = link.absUrl("href").toHttpUrlOrNull()?.pathSegments?.getOrNull(1)
                ?: return@mapNotNull null
            GenreOption(name, slug)
        }
        .distinctBy { it.slug }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreOption>>())

    // =============================== Related ==============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val relatedContainer = document.select("h2.card-title")
            .firstOrNull { it.text().contains("Truyện liên quan") }
            ?.parent()
            ?: return emptyList()

        return relatedContainer.select(".card.card-manga")
            .map(::mangaFromElement)
            .distinctBy { it.url }
    }

    // ============================= Utilities =============================

    private fun Element.extractImageUrl(): String? = absUrl("src")
        .ifEmpty { absUrl("data-src") }
        .ifEmpty { null }

    private val multipleSpacesRegex = Regex("\\s+")
    private val relativeDateNumberRegex = Regex("\\d+")
    private val chapterDateTimeFormat = DateTimeFormatter.ofPattern("HH:mm dd-MM-yyyy", Locale.ROOT)
    private val chapterDateFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ROOT)
    private val vietnamZone = ZoneId.of("Asia/Ho_Chi_Minh")
}
