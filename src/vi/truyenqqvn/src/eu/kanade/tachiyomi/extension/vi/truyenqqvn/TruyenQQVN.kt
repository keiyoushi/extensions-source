package eu.kanade.tachiyomi.extension.vi.truyenqqvn

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class TruyenQQVN : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2, 1.seconds)

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaPage(client.get(listUrl("/truyen-hot", page)))

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaPage(client.get(listUrl("/truyen-moi", page)))

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            "$baseUrl/tim-kiem".toHttpUrl().newBuilder()
                .addQueryParameter("s", query)
                .apply { if (page > 1) addQueryParameter("page", page.toString()) }
                .build()
        } else {
            val genre = filters.firstInstanceOrNull<GenreFilter>()?.selected?.takeIf { it.isNotEmpty() }
            val path = genre?.let { "/the-loai/$it" }
                ?: filters.firstInstanceOrNull<ListFilter>()?.selected
                ?: "/truyen-hot"
            listUrl(path, page)
        }

        return parseMangaPage(client.get(url))
    }

    private fun listUrl(path: String, page: Int): HttpUrl = "$baseUrl$path".toHttpUrl().newBuilder()
        .apply { if (page > 1) addQueryParameter("page", page.toString()) }
        .build()

    private fun parseMangaPage(response: Response): MangasPage {
        val document = response.asJsoup()

        val entries = document.select(".inner .item").map { element ->
            SManga.create().apply {
                val anchor = element.selectFirst(".info h3 a")!!
                setUrlWithoutDomain(anchor.absUrl("href"))
                title = anchor.text()
                thumbnail_url = element.selectFirst(".cover img")?.absUrl("src")
            }
        }

        val nextPage = (response.request.url.queryParameter("page")?.toIntOrNull() ?: 1) + 1
        val hasNextPage = document.select(".pagination a").any { link ->
            link.absUrl("href").toHttpUrlOrNull()?.queryParameter("page")?.toIntOrNull() == nextPage
        }

        return MangasPage(entries, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        // Series live at /<slug> and their chapters at /<slug>/chapter-<n>; both resolve to the series.
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.size > 2) return null

        val slug = segments.firstOrNull()?.takeIf { it !in RESERVED_PATHS } ?: return null
        val path = "/$slug"

        return parseMangaDetails(client.get("$baseUrl$path").asJsoup()).apply {
            setUrlWithoutDomain(path)
        }
    }

    // =========================== Manga Details ============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        // Details and the full chapter list come from the same page.
        val document = client.get(getMangaUrl(manga)).asJsoup()

        return SMangaUpdate(
            manga = parseMangaDetails(document),
            chapters = parseChapterList(document),
        )
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1[itemprop=name]")!!.text()
        thumbnail_url = document.selectFirst(".book-info .poster img")?.absUrl("src")
        description = document.selectFirst("[itemprop=description]")?.text()
        // The site writes this placeholder into the author field when it has no real value.
        author = document.metaResult("Tác giả")?.text()?.takeIf { it.isNotEmpty() && it != "Đang cập nhật" }
        genre = document.select(".book-meta a[href*=/the-loai/]").joinToString { it.text() }
        status = when (document.selectFirst(".label-status")?.text()?.lowercase()) {
            "đang ra" -> SManga.ONGOING
            "hoàn thành" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private fun Document.metaResult(label: String): Element? = select(".book-meta .line")
        .firstOrNull { it.selectFirst(".title")?.text()?.startsWith(label) == true }
        ?.selectFirst(".result")

    // ============================== Chapters ==============================

    private fun parseChapterList(document: Document): List<SChapter> = document.select(".reading-list .item").map { element ->
        SChapter.create().apply {
            val anchor = element.selectFirst("a")!!
            setUrlWithoutDomain(anchor.absUrl("href"))
            name = anchor.text()
            date_upload = parseChapterDate(element.selectFirst(".item-time")?.text())
        }
    }

    // Recent chapters carry a relative label ("2 giờ trước"), older ones a plain dd/MM/yyyy date.
    private fun parseChapterDate(date: String?): Long {
        val text = date?.lowercase() ?: return 0L

        if (text.contains("hôm qua")) {
            return System.currentTimeMillis() - 1.days.inWholeMilliseconds
        }

        relativeDateRegex.find(text)?.let { match ->
            val amount = match.groupValues[1].toLong()
            val elapsed = when (match.groupValues[2]) {
                "giây" -> amount.seconds
                "phút" -> amount.minutes
                "giờ" -> amount.hours
                "ngày" -> amount.days
                "tuần" -> (amount * 7).days
                "tháng" -> (amount * 30).days
                else -> (amount * 365).days
            }

            return System.currentTimeMillis() - elapsed.inWholeMilliseconds
        }

        return dateFormat.tryParseDate(text, TIME_ZONE)
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()

        return document.select("img.lazy[data-src]").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("data-src"))
        }
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get(baseUrl).asJsoup()
        .select("a[href*=/the-loai/]")
        .mapNotNull { element ->
            val slug = element.absUrl("href").toHttpUrlOrNull()
                ?.pathSegments?.getOrNull(1)
                ?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val name = element.text().takeIf { it.isNotEmpty() } ?: return@mapNotNull null

            Genre(name, slug)
        }
        .distinctBy { it.slug }
        .sortedBy { it.name }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<Genre>>().orEmpty()

        return FilterList(
            buildList {
                add(Filter.Header("Bộ lọc bị bỏ qua khi tìm kiếm bằng tên"))
                add(ListFilter())
                if (genres.isNotEmpty()) {
                    add(GenreFilter(genres))
                }
            },
        )
    }

    companion object {
        private val RESERVED_PATHS = setOf("tim-kiem", "truyen-hot", "truyen-moi", "truyen-full", "the-loai")
        private val TIME_ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
        private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
        private val relativeDateRegex = Regex("""(\d+)\s*(giây|phút|giờ|ngày|tuần|tháng|năm)""")
    }
}
