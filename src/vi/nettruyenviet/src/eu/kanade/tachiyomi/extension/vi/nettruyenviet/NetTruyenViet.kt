package eu.kanade.tachiyomi.extension.vi.nettruyenviet

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@Source
abstract class NetTruyenViet : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(5)

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/tim-truyen".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "10")
            .addQueryParameter("page", page.toString())
            .build()
        return parseMangaPage(client.get(url, Fetch.DOCUMENT).asJsoup())
    }

    private fun parseMangaPage(document: Document): MangasPage {
        val mangaList = document.select("div.items div.row > div.item").map(::mangaFromElement)
        return MangasPage(mangaList, hasNextPage(document))
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val linkElement: Element = element.selectFirst("figcaption h3 a.jtip, figcaption h3 a, .slide-caption h3 a")!!
        title = linkElement.text()
        setUrlWithoutDomain(linkElement.absUrl("href"))
        thumbnail_url = element.selectFirst("div.image img, img.image-thumb, a > img")
            ?.run {
                absUrl("data-original")
                    .ifEmpty { absUrl("data-src") }
                    .ifEmpty { absUrl("src") }
            }
            ?.takeUnless { it.isBlank() }
    }

    private fun hasNextPage(document: Document): Boolean = document.select("ul.pagination li.page-item:not(.disabled) > a")
        .any { link -> link.text() == NEXT_PAGE_SYMBOL }

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        // The root is now a landing page without the list
        val url = "$baseUrl/truyen-tranh-moi".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        return parseMangaPage(client.get(url, Fetch.DOCUMENT).asJsoup())
    }

    // ============================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/tim-truyen".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .addQueryParameter("page", page.toString())
                .build()
            return parseMangaPage(client.get(url, Fetch.DOCUMENT).asJsoup())
        }

        val genrePath = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()
        val websitePath = filters.firstInstanceOrNull<WebsiteFilter>()?.toUriPart()
        val sortValue = filters.firstInstanceOrNull<SortFilter>()?.toUriPart()
        val statusValue = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart()

        val path = websitePath ?: genrePath ?: "/tim-truyen"
        val url = "$baseUrl$path".toHttpUrl().newBuilder().apply {
            if (path.startsWith("/tim-truyen")) {
                sortValue?.let { addQueryParameter("sort", it) }
                statusValue?.let { addQueryParameter("status", it) }
            }
            addQueryParameter("page", page.toString())
        }.build()

        return parseMangaPage(client.get(url, Fetch.DOCUMENT).asJsoup())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if ((url.host != baseUrl.toHttpUrl().host && url.host !in SITE_HOSTS) || url.pathSegments.firstOrNull() != "truyen-tranh") {
            return null
        }
        val slug = url.pathSegments.getOrNull(1)?.ifEmpty { null } ?: return null
        val manga = SManga.create().apply { this.url = "/truyen-tranh/$slug" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // ============================== Details ===============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { if (fetchDetails) fetchMangaDetails(manga) else manga }
        val chapterList = async { if (fetchChapters) fetchChapterList(manga) else chapters }
        SMangaUpdate(details.await(), chapterList.await())
    }

    private suspend fun fetchMangaDetails(manga: SManga): SManga {
        val infoElement = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup().selectFirst("article#item-detail")!!

        return manga.apply {
            title = infoElement.selectFirst("h1.title-detail, h1")!!.text()
            author = infoElement.selectFirst("li.author p.col-xs-8")?.text()
            status = infoElement.selectFirst("li.status p.col-xs-8")?.text().toStatus()
            genre = infoElement.select("li.kind p.col-xs-8 a")
                .joinToString { it.text() }
                .takeIf { it.isNotEmpty() }
                .takeUnless { it.equals("Đang cập nhật", ignoreCase = true) }
            thumbnail_url = infoElement.selectFirst("div.col-image img")
                ?.run {
                    absUrl("src")
                        .ifEmpty { absUrl("data-original") }
                        .ifEmpty { absUrl("data-src") }
                }
                ?.takeUnless { it.isBlank() }
            description = infoElement.selectFirst("div.detail-content div.shortened, div.detail-content")
                ?.clone()
                ?.apply {
                    select("h2.list-title, a.morelink, script, style").remove()
                }
                ?.text()
                ?.takeUnless { it.isBlank() }
        }
    }

    private fun String?.toStatus(): Int = when {
        this == null -> SManga.UNKNOWN
        contains("Đang tiến hành", ignoreCase = true) -> SManga.ONGOING
        contains("Đang cập nhật", ignoreCase = true) -> SManga.ONGOING
        contains("Hoàn thành", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        val slug = manga.url.substringAfter("/truyen-tranh/").substringBefore("/")
            .ifEmpty { manga.url.substringAfterLast("/") }

        val url = "$baseUrl/Comic/Services/ComicService.asmx/ChapterList".toHttpUrl().newBuilder()
            .addQueryParameter("slug", slug)
            .build()

        val chapterHeaders = headers.newBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val chapterItems = client.get(url, chapterHeaders, Fetch.XHR).parseAs<ChapterListDto>().toChapterItems(slug)
        return chapterItems.map { chapterItem ->
            SChapter.create().apply {
                name = chapterItem.name
                setUrlWithoutDomain(chapterItem.url)
                date_upload = parseChapterDate(chapterItem.updatedAt)
                chapter_number = chapterItem.chapterNumber
            }
        }
    }

    private fun parseChapterDate(rawDate: String): Long = chapterDateFormat.tryParseDateTime(rawDate, VN_ZONE).takeIf { it > 0L } ?: parseRelativeDate(rawDate)

    private fun parseRelativeDate(dateText: String?): Long {
        if (dateText.isNullOrBlank()) return 0L

        val number = RELATIVE_DATE_NUMBER_REGEX.find(dateText)?.value?.toIntOrNull() ?: return 0L
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))

        when {
            dateText.contains("giây", ignoreCase = true) -> calendar.add(Calendar.SECOND, -number)
            dateText.contains("phút", ignoreCase = true) -> calendar.add(Calendar.MINUTE, -number)
            dateText.contains("giờ", ignoreCase = true) -> calendar.add(Calendar.HOUR_OF_DAY, -number)
            dateText.contains("ngày", ignoreCase = true) -> calendar.add(Calendar.DAY_OF_MONTH, -number)
            dateText.contains("tuần", ignoreCase = true) -> calendar.add(Calendar.WEEK_OF_YEAR, -number)
            dateText.contains("tháng", ignoreCase = true) -> calendar.add(Calendar.MONTH, -number)
            dateText.contains("năm", ignoreCase = true) -> calendar.add(Calendar.YEAR, -number)
            else -> return 0L
        }

        return calendar.timeInMillis
    }

    // ============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val imageUrls = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
            .select("#chapter-content img, .reading-detail .page-chapter img, .chapter-content img, .page-chapter img")
            .map { imageElement ->
                imageElement.absUrl("data-src")
                    .ifEmpty { imageElement.absUrl("data-original") }
                    .ifEmpty { imageElement.absUrl("src") }
            }
            .filterNot { it.isEmpty() || SITE_IMAGES_PATH in it }

        if (imageUrls.isEmpty()) {
            throw Exception("Không tìm thấy hình ảnh")
        }

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    // ============================== Filters ================================

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(
        genres = GenreFilterOptions,
        websites = WebsiteFilterOptions,
    )

    companion object {
        private val RELATIVE_DATE_NUMBER_REGEX = Regex("\\d+")

        // The site's logo or notice image, placed before or after the pages depending on the domain
        private const val SITE_IMAGES_PATH = "/assets/images/"
        private const val NEXT_PAGE_SYMBOL = "›"
        private val VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh")
        private val chapterDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

        // The site serves the same pages on these domains, so their links open with the chosen base URL
        private val SITE_HOSTS = setOf(
            "nettruyenviet10.com",
            "www.nettruyenviet10.com",
            "nettruyenviet11.com",
            "www.nettruyenviet11.com",
            "hinettruyen.com",
            "www.hinettruyen.com",
        )
    }
}
