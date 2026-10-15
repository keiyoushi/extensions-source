package eu.kanade.tachiyomi.extension.vi.leesincomic

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
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class LeesinComic : KeiSource() {
    // CDN returns 404 when Origin is present
    override fun Headers.Builder.configureHeaders(): Headers.Builder = removeAll("Origin")

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(3)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList("$baseUrl/top-ngay.html?page=$page")

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList("$baseUrl/truyen-moi-cap-nhat.html?page=$page")

    // ============================== Search ================================

    // Filters cannot be combined with keyword search on this site
    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/tim-kiem.html".toHttpUrl().newBuilder()
                .addQueryParameter("key", query)
                .addQueryParameter("page", page.toString())
                .build()
            return getMangaList(url.toString())
        }

        val filterPath = filters.buildBrowsePath()
        return getMangaList("$baseUrl$filterPath${filterPath.pageSeparator()}page=$page")
    }

    private fun FilterList.buildBrowsePath(): String {
        firstInstanceOrNull<GroupFilter>()?.toUriPart()?.takeIf { it.isNotBlank() }?.let { return it }
        firstInstanceOrNull<GenreFilter>()?.toUriPart()?.takeIf { it.isNotBlank() }?.let { return it }
        firstInstanceOrNull<TypeFilter>()?.toUriPart()?.takeIf { it.isNotBlank() }?.let { return it }
        return "/top-ngay.html"
    }

    private fun String.pageSeparator(): String = if (contains("?")) "&" else "?"

    private suspend fun getMangaList(url: String): MangasPage {
        val document = client.get(url).asJsoup()
        val mangas = document.select(".box_list .li_truyen").map { element ->
            SManga.create().apply {
                val link = element.selectFirst("a[href*=/truyen-tranh/]")!!
                setUrlWithoutDomain(link.absUrl("href").ifBlank { link.attr("href") })
                title = element.selectFirst(".name")!!.text()
                val img = element.selectFirst(".img img") ?: element.selectFirst("img")
                thumbnail_url = img?.let { resolveImageUrl(it) }?.takeIf { it.isNotBlank() }
            }
        }
        val hasNextPage = document.select(".page_redirect a").any {
            it.ownText().trim() == (currentPageFromUrl(url) + 1).toString()
        }
        return MangasPage(mangas, hasNextPage)
    }

    private fun currentPageFromUrl(url: String): Int = url.substringAfter("page=", "1").substringBefore("&").toIntOrNull() ?: 1

    // tachserver.online drops connections; identical paths are served by tachserver.site
    private fun resolveImageUrl(img: Element): String {
        val raw = img.attrOrNull("data-src") ?: img.attrOrNull("src").orEmpty()
        val absolute = when {
            raw.isBlank() -> return ""
            raw.startsWith("http") -> raw
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("/") -> baseUrl + raw
            else -> "$baseUrl/$raw"
        }
        return absolute.replace("://tachserver.online/", "://tachserver.site/")
    }

    // ============================== Details ===============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document, manga),
            chapters = parseChapterList(document),
        )
    }

    private fun parseMangaDetails(document: Document, manga: SManga): SManga {
        val info = document.selectFirst(".box_info_right")!!
        return manga.apply {
            title = info.selectFirst("h1")!!.text()
            thumbnail_url = document.selectFirst(".box_info_left img")
                ?.let { resolveImageUrl(it) }
                ?.takeIf { it.isNotBlank() }
            genre = info.select(".list-tag-story a").joinToString { it.text() }

            val otherName = info.selectFirst(".txt span.info-item")?.text()?.trim()
            val description = info.selectFirst(".story-detail-info")?.text()?.trim().orEmpty()
            this.description = buildString {
                if (!otherName.isNullOrBlank()) {
                    appendLine(otherName.replaceFirst("Tên Khác", "Tên khác"))
                    appendLine()
                }
                append(description)
            }

            val statusText = info.select(".txt p.info-item")
                .firstOrNull { it.text().contains("Tình trang") || it.text().contains("Tình trạng") }
                ?.text()
                ?.substringAfter(":")
                ?.trim()
                ?.lowercase()
                .orEmpty()
            status = when {
                statusText.contains("cập nhật") -> SManga.ONGOING
                statusText.contains("hoàn thành") || statusText.contains("full") -> SManga.COMPLETED
                statusText.contains("tạm ngưng") || statusText.contains("ngưng") -> SManga.ON_HIATUS
                statusText.contains("hủy") -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
    }

    private fun parseChapterList(document: Document): List<SChapter> = document.select(".list-chapters .chapter-item").map { element ->
        SChapter.create().apply {
            val link = element.selectFirst(".chap_name a")!!
            setUrlWithoutDomain(link.absUrl("href").ifBlank { link.attr("href") })
            name = link.ownText().trim()
            date_upload = parseRelativeDate(element.selectFirst(".chap_update")?.text())
        }
    }

    private fun parseRelativeDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L

        when {
            dateStr.contains("Vừa xong") -> return Clock.System.now().toEpochMilliseconds()
            dateStr.contains("Hôm nay") -> return Clock.System.now().toEpochMilliseconds()
            dateStr.contains("Hôm qua") -> return (Clock.System.now() - 1.days).toEpochMilliseconds()
        }

        val number = numberRegex.find(dateStr)?.value?.toIntOrNull()
            ?: return dateFormat.tryParseDate(dateStr, siteZone)

        val duration = when {
            dateStr.contains("giây") -> number.seconds
            dateStr.contains("phút") -> number.minutes
            dateStr.contains("giờ") -> number.hours
            dateStr.contains("ngày") -> number.days
            dateStr.contains("tuần") -> (number * 7).days
            dateStr.contains("tháng") -> (number * 30).days
            dateStr.contains("năm") -> (number * 365).days
            else -> return dateFormat.tryParseDate(dateStr, siteZone)
        }

        return (Clock.System.now() - duration).toEpochMilliseconds()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        if (segments.size < 2 || segments[0] != "truyen-tranh") return null

        val slug = if (segments.size == 2) {
            segments[1].removeSuffix(".html")
        } else {
            segments[1]
        }
        if (slug.isBlank()) return null

        return fetchMangaUpdate(
            SManga.create().apply { setUrlWithoutDomain("/truyen-tranh/$slug.html") },
            emptyList(),
            fetchDetails = true,
            fetchChapters = false,
        ).manga
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url).asJsoup()
        return document.select(".content_view_chap img").mapIndexed { index, img ->
            Page(index, imageUrl = resolveImageUrl(img))
        }
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val homeDeferred = async { client.get(baseUrl).asJsoup() }
        val groupsDeferred = async { client.get("$baseUrl/danh-sach-nhom-dich.html").asJsoup() }
        val home = homeDeferred.await()
        val groupsDocument = groupsDeferred.await()

        FilterData(
            types = home.parseTypeOptions(),
            genres = home.parseGenreOptions(),
            groups = groupsDocument.parseGroupOptions(),
        ).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<FilterData>())

    private fun Document.parseTypeOptions(): List<FilterOption> = select(".main_menu > li.li_main > a").mapNotNull { element ->
        val href = element.attr("href").trim()
        val name = element.text().trim()
        if (href.isBlank() || href == "/" || href.startsWith("javascript") || href.startsWith("http")) {
            return@mapNotNull null
        }
        if (href.contains("danh-sach-nhom-dich") || href.contains("the-loai")) return@mapNotNull null
        if (name.isBlank()) return@mapNotNull null
        FilterOption(name, href)
    }

    private fun Document.parseGenreOptions(): List<FilterOption> = select(".sub_menu a[href*=/the-loai/]").mapNotNull { element ->
        val href = element.attr("href").trim()
        val name = element.ownText().trim().ifBlank { element.text().trim() }
        if (href.isBlank() || name.isBlank()) return@mapNotNull null
        FilterOption(name, href)
    }.distinctBy { it.path }

    private fun Document.parseGroupOptions(): List<FilterOption> = select(".box_list .li_truyen > a[href*=/nhom-dich-]").mapNotNull { element ->
        val href = element.attr("href").trim()
        val name = element.selectFirst(".name")?.text()?.trim()
        if (href.isBlank() || name.isNullOrBlank()) return@mapNotNull null
        FilterOption(name, href)
    }

    private val numberRegex = Regex("""\d+""")
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    private val siteZone = ZoneId.of("Asia/Ho_Chi_Minh")
}
