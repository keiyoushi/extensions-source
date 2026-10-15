package eu.kanade.tachiyomi.extension.vi.truyenggvn

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
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class TruyenGGVN : KeiSource() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(3)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/tim-kiem-nang-cao/trang-$page.html?status=-1&country=0&sort=4&category=&notcategory=&minchapter=0"
        return parseMangaPage(client.get(url))
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/tim-kiem-nang-cao/trang-$page.html?status=-1&country=0&sort=2&category=&notcategory=&minchapter=0"
        return parseMangaPage(client.get(url))
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            "$baseUrl/tim-kiem/trang-$page.html".toHttpUrl().newBuilder()
                .addQueryParameter("q", query.trim())
                .build()
        } else {
            var status = "-1"
            var country = "0"
            var sort = "2"
            var minchapter = "0"
            var category = ""
            var notcategory = ""

            filters.forEach { filter ->
                when (filter) {
                    is CountryFilter -> country = filter.selected
                    is StatusFilter -> status = filter.selected
                    is ChapterCountFilter -> minchapter = filter.selected
                    is SortByFilter -> filter.state?.let {
                        sort = (it.index * 2 + if (it.ascending) 1 else 0).toString()
                    }
                    is GenreList -> {
                        category = filter.state
                            .filter { it.state == Filter.TriState.STATE_INCLUDE }
                            .joinToString(",") { it.id }
                        notcategory = filter.state
                            .filter { it.state == Filter.TriState.STATE_EXCLUDE }
                            .joinToString(",") { it.id }
                    }
                    else -> {}
                }
            }

            "$baseUrl/tim-kiem-nang-cao/trang-$page.html".toHttpUrl().newBuilder()
                .addQueryParameter("status", status)
                .addQueryParameter("country", country)
                .addQueryParameter("sort", sort)
                .addQueryParameter("category", category)
                .addQueryParameter("notcategory", notcategory)
                .addQueryParameter("minchapter", minchapter)
                .build()
        }

        return parseMangaPage(client.get(url))
    }

    private fun parseMangaPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".list_grid li").mapNotNull { element ->
            val anchor = element.selectFirst(".book_info h3 a") ?: return@mapNotNull null
            val img = element.selectFirst(".book_avatar img")

            SManga.create().apply {
                setUrlWithoutDomain(anchor.absUrl("href"))
                title = anchor.text().trim()
                thumbnail_url = img?.let {
                    it.absUrl("src").ifEmpty { it.absUrl("data-original") }
                }
            }
        }

        val hasNextPage = document.select(".page_redirect a").any { it.text().contains("›") }
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.size != 2 || segments[0] != "truyen-tranh") return null

        val mangaSlug = segments[1].substringBefore("-chap-")
        val mangaPath = "/truyen-tranh/$mangaSlug"

        val document = client.get("$baseUrl$mangaPath").asJsoup()
        return parseMangaDetails(document).apply {
            setUrlWithoutDomain(mangaPath)
        }
    }

    // =========================== Manga Details ============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(
            parseMangaDetails(document),
            parseChapterList(document),
        )
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1[itemprop=name]")!!.text().trim()
        thumbnail_url = document.selectFirst(".book_avatar img, img[itemprop=image]")?.let {
            it.absUrl("src").ifEmpty { it.absUrl("data-original") }
        }
        author = document.select(".book-counts li").firstOrNull {
            it.selectFirst(".book-counts__label")?.text()?.contains("Tác giả") == true
        }?.selectFirst(".book-counts__value")?.text()?.trim()

        genre = document.select(".book-genres a, .list01 a").joinToString { it.text().trim() }

        description = document.select(".story-detail-info.detail-content, .story-detail-info")
            .joinToString("\n\n") { it.wholeText().trim() }
            .ifEmpty { null }

        status = parseStatus(
            document.select(".book-counts li").firstOrNull {
                it.selectFirst(".book-counts__label")?.text()?.contains("Tình trạng") == true
            }?.selectFirst(".book-counts__value")?.text()?.trim(),
        )
    }

    private fun parseStatus(status: String?): Int {
        val s = status?.lowercase() ?: return SManga.UNKNOWN
        return when {
            s.contains("đang cập nhật") || s.contains("đang tiến hành") || s.contains("đang ra") -> SManga.ONGOING
            s.contains("hoàn thành") || s.contains("đã hoàn thành") -> SManga.COMPLETED
            s.contains("tạm ngưng") || s.contains("tạm hoãn") -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private fun parseChapterList(document: Document): List<SChapter> = document.select(".works-chapter-list .works-chapter-item").mapNotNull { element ->
        val anchor = element.selectFirst("a") ?: return@mapNotNull null
        SChapter.create().apply {
            setUrlWithoutDomain(anchor.absUrl("href"))
            name = anchor.text().trim()
            date_upload = dateFormat.tryParseDate(element.selectFirst(".time-chap")?.text()?.trim(), dateZone)
        }
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), CacheControl.FORCE_NETWORK).asJsoup()
        return document.select(".page-chapter img:not([src*='stress.gif'])").mapIndexedNotNull { index, element ->
            val url = element.absUrl("data-original")
                .ifEmpty { element.absUrl("data-cdn") }
                .ifEmpty { element.absUrl("src") }
                .takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null

            Page(index, imageUrl = url)
        }
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/tim-kiem-nang-cao.html").asJsoup()
        .select(".advsearch-form .genre-list .genre-item")
        .mapNotNull { element ->
            val id = element.selectFirst("span[data-id]")?.attr("data-id") ?: return@mapNotNull null
            val name = element.text().trim()
            GenreDto(name, id)
        }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreDto>>()

        return FilterList(
            Filter.Header("Bộ lọc không hoạt động khi tìm kiếm"),
            CountryFilter(),
            StatusFilter(),
            ChapterCountFilter(),
            SortByFilter(),
            GenreList(genres.orEmpty().map { Genre(it.name, it.id) }),
        )
    }

    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    private val dateZone = ZoneId.of("Asia/Ho_Chi_Minh")
}
