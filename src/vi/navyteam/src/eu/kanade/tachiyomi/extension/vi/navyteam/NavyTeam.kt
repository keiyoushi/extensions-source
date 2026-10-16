package eu.kanade.tachiyomi.extension.vi.navyteam

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
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class NavyTeam : KeiSource() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(3)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/danh-sach-truyen/truyen-tranh".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        val document = client.get(url).asJsoup()
        val mangas = document.select(".comic-item").mapNotNull { card ->
            val link = card.selectFirst("a[href*=/truyen/]") ?: return@mapNotNull null
            val title = card.selectFirst(".comic-title")?.text()?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                this.title = title
                thumbnail_url = card.selectFirst(".comic-cover img")?.absUrl("src")
            }
        }
        val hasNextPage = document.select(".pagination a, nav a")
            .any { link ->
                link.absUrl("href").toHttpUrl().queryParameter("page") == (page + 1).toString()
            }

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ================================

    override val supportsLatest = false

    override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(emptyList(), false)

    // ============================== Search ================================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        if (page > 1 || query.isBlank()) return MangasPage(emptyList(), false)

        val url = "$baseUrl/api/search-story".toHttpUrl().newBuilder()
            .addQueryParameter("keyword", query)
            .build()
        val mangas = client.get(url).parseAs<List<SearchResult>>().map(SearchResult::toSManga)
        return MangasPage(mangas, false)
    }

    // ============================== Details ===============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "truyen") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotBlank) ?: return null
        val manga = SManga.create().apply { this.url = "/truyen/$slug" }
        return fetchMangaUpdate(manga, emptyList(), true, true).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document, manga.url),
            chapters = parseChapterList(document),
        )
    }

    private fun parseMangaDetails(document: Document, mangaUrl: String) = SManga.create().apply {
        url = mangaUrl
        title = document.selectFirst(".info-title h2")!!.text()
        thumbnail_url = document.selectFirst(".info-cover img")?.absUrl("src")
        author = document.findValue("Tác giả")
        genre = document.selectFirst(".tags")?.select("a[href*=/the-loai/]")
            .orEmpty()
            .map { it.text().removePrefix("#").trim() }
            .filter(String::isNotBlank)
            .distinct()
            .joinToString()
            .ifEmpty { null }
        description = document.selectFirst(".pop-description, .description, .story-description")?.text()
        status = when (document.findValue("Tình trạng")?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private fun Document.findValue(label: String): String? = select(".info-row")
        .firstOrNull { it.selectFirst(".info-label")?.text() == label }
        ?.selectFirst(".info-value")
        ?.text()
        ?.takeIf(String::isNotBlank)

    private fun parseChapterList(document: Document): List<SChapter> = document
        .select(".episode-title a[href*=/truyen/][href*=/chap-]")
        .map { link ->
            SChapter.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                name = link.selectFirst("h3")!!.text()
                date_upload = parseChapterDate(link.selectFirst(".chapter-date")?.text())
            }
        }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> = client
        .get(getChapterUrl(chapter))
        .asJsoup()
        .select("img.manga-img")
        .mapIndexed { index, image -> Page(index, imageUrl = image.absUrl("src")) }

    private fun parseChapterDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L

        val normalized = dateStr.lowercase()
        if ("hôm nay" in normalized || "vừa" in normalized) {
            return Clock.System.now().toEpochMilliseconds()
        }
        if ("hôm qua" in normalized) {
            return (Clock.System.now() - 1.days).toEpochMilliseconds()
        }

        val number = numberRegex.find(normalized)?.value?.toIntOrNull()
            ?: return dateFormat.tryParseDate(dateStr, siteZone)
        val duration = when {
            "giây" in normalized -> number.seconds
            "phút" in normalized -> number.minutes
            "giờ" in normalized -> number.hours
            "ngày" in normalized -> number.days
            "tuần" in normalized -> (number * 7).days
            "tháng" in normalized -> (number * 30).days
            "năm" in normalized -> (number * 365).days
            else -> return dateFormat.tryParseDate(dateStr, siteZone)
        }
        return (Clock.System.now() - duration).toEpochMilliseconds()
    }

    private val numberRegex = Regex("""\d+""")
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    private val siteZone = ZoneId.of("Asia/Ho_Chi_Minh")

    @Serializable
    private class SearchResult(
        private val slug: String,
        private val title: String,
        private val cover: String,
    ) {
        fun toSManga() = SManga.create().apply {
            url = "/truyen/$slug"
            title = this@SearchResult.title
            thumbnail_url = cover
        }
    }
}
