package eu.kanade.tachiyomi.extension.vi.truyenqq

import eu.kanade.tachiyomi.source.model.Filter
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
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class TruyenQQ : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(1, 2.seconds) { it.host == baseUrl.toHttpUrl().host }
    }

    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/truyen-yeu-thich" + if (page > 1) "/trang-$page" else ""

        return parseMangaPage(client.get(url))
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/truyen-moi-cap-nhat" + if (page > 1) "/trang-$page" else ""

        return parseMangaPage(client.get(url))
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val endpoint = if (query.isNotBlank()) "tim-kiem" else "tim-kiem-nang-cao"
        val url = ("$baseUrl/$endpoint" + if (page > 1) "/trang-$page" else "").toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            } else {
                filters.forEach { filter ->
                    when (filter) {
                        is CountryFilter -> addQueryParameter("country", filter.values[filter.state].id)
                        is StatusFilter -> addQueryParameter("status", filter.values[filter.state].id)
                        is ChapterCountFilter -> addQueryParameter("minchapter", filter.values[filter.state].id)
                        is SortByFilter -> filter.state?.let {
                            addQueryParameter("sort", (it.index * 2 + (if (it.ascending) 1 else 0)).toString())
                        }
                        is GenreList -> {
                            addQueryParameter(
                                "category",
                                filter.state.asSequence()
                                    .filter { it.state == Filter.TriState.STATE_INCLUDE }
                                    .joinToString(",") { it.id },
                            )
                            addQueryParameter(
                                "notcategory",
                                filter.state.asSequence()
                                    .filter { it.state == Filter.TriState.STATE_EXCLUDE }
                                    .joinToString(",") { it.id },
                            )
                        }
                        else -> {}
                    }
                }
            }
        }.build()

        return parseMangaPage(client.get(url))
    }

    private fun parseMangaPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val manga = document.select("ul.grid > li").map { element ->
            SManga.create().apply {
                val anchor = element.selectFirst(".book_info .qtip a")!!
                setUrlWithoutDomain(anchor.attr("href"))
                title = anchor.text()
                thumbnail_url = element.selectFirst(".book_avatar img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst(".page_redirect > a:nth-last-child(2) > p:not(.active)") != null
        return MangasPage(manga, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host == baseUrl.toHttpUrl().host) {
            client.get(url, headers).use { response ->
                return parseMangaDetails(response.asJsoup())
            }
        }
        return null
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
        val info = document.selectFirst(".list-info")!!

        title = document.select("h1").text()
        author = info.select(".org").joinToString { it.text() }
        genre = document.select(".list01 li").joinToString { it.text() }
        description = document.select(".story-detail-info")
            .joinToString("\n\n") { container ->
                val blocks = container.select("p")
                if (blocks.isNotEmpty()) blocks.joinToString("\n\n") { it.wholeText().trim() } else container.wholeText().trim()
            }

        thumbnail_url = document.selectFirst("img[itemprop=image]")?.absUrl("src")
        status = parseStatus(info.select(".status > p:last-child").text())
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        listOf("Đang Tiến Hành", "Đang Cập Nhật", "Đang Ra").any { status.contains(it, ignoreCase = true) } -> SManga.ONGOING
        listOf("Hoàn Thành", "Đã Hoàn Thành").any { status.contains(it, ignoreCase = true) } -> SManga.COMPLETED
        listOf("Tạm Ngưng", "Tạm Hoãn").any { status.contains(it, ignoreCase = true) } -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    private fun parseChapterList(document: Document): List<SChapter> = document.select("div.works-chapter-list div.works-chapter-item").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
            name = element.select("a").text().trim()
            date_upload = dateFormat.tryParse(element.select(".time-chap").text())
        }
    }

    private fun DateTimeFormatter.tryParse(date: String): Long = runCatching {
        LocalDate.parse(date, this)
            .atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh"))
            .toInstant()
            .toEpochMilli()
    }.getOrDefault(0L)

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val cacheControl = CacheControl.FORCE_NETWORK
        client.get(getChapterUrl(chapter), cacheControl).use { response ->
            return response.asJsoup()
                .select(".page-chapter img:not([src*='stress.gif'])")
                .mapIndexed { idx, it ->
                    Page(idx, imageUrl = it.absUrl("src"))
                }
        }
    }

    // ============================== Filters ===============================

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("Không dùng chung với tìm kiếm bằng tên"),
        CountryFilter(),
        StatusFilter(),
        ChapterCountFilter(),
        SortByFilter(),
        GenreList(getGenreList()),
    )
}
