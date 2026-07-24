package eu.kanade.tachiyomi.extension.vi.truyengg

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
abstract class TruyenGG : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(1, 2.seconds) { it.host == baseUrl.toHttpUrl().host }
    }

    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/top-binh-chon" + if (page > 1) "/trang-$page.html" else ""

        return parseMangaPage(client.get(url))
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/truyen-moi-cap-nhat" + if (page > 1) "/trang-$page.html" else ""

        return parseMangaPage(client.get(url))
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val endpoint = if (query.isNotBlank()) "tim-kiem" else "tim-kiem-nang-cao"
        val url = ("$baseUrl/$endpoint" + if (page > 1) "/trang-$page.html" else "").toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            } else {
                filters.forEach { filter ->
                    when (filter) {
                        is CountryFilter -> addQueryParameter("country", filter.values[filter.state].id)
                        is StatusFilter -> addQueryParameter("status", filter.values[filter.state].id)
                        is ChapterCountFilter -> addQueryParameter("minchapter", filter.values[filter.state].id)
                        is SortByFilter -> filter.state?.let {
                            addQueryParameter("sort", (it.index * 2 + if (it.ascending) 1 else 0).toString())
                        }
                        is GenreList -> {
                            addQueryParameter(
                                "category",
                                filter.state.filter { it.state == Filter.TriState.STATE_INCLUDE }
                                    .joinToString(",") { it.id },
                            )
                            addQueryParameter(
                                "notcategory",
                                filter.state.filter { it.state == Filter.TriState.STATE_EXCLUDE }
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
        val mangas = document.select(".list_item_home .item_home").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a.book_name")!!.absUrl("href"))
                title = element.select("a.book_name").text()
                thumbnail_url = element.selectFirst(".image-cover img")?.absUrl("data-src")
            }
        }
        val hasNextPage = document.selectFirst(".pagination a.active + a") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host == baseUrl.toHttpUrl().host) {
            client.get(url).use { response ->
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
        title = document.select("h1[itemprop=name]").text()
        author = document.selectFirst("span:contains(Tác Giả) + span")?.text()
        genre = document.select(".fx-genres a").joinToString { it.text() }
        description = document.selectFirst("div.fx-synopsis div")?.wholeText()?.trim()
        thumbnail_url = document.selectFirst(".fx-cover img")?.absUrl("src")
        status = parseStatus(document.select(".fx-status").text())
    }

    private fun parseStatus(status: String?): Int {
        val ongoingWords = listOf("Đang Cập Nhật", "Đang Tiến Hành", "Còn tiếp", "Đang ra")
        val completedWords = listOf("Hoàn Thành", "Đã Hoàn Thành", "Hoàn")
        val hiatusWords = listOf("Tạm ngưng", "Tạm hoãn", "Bị drop")
        return when {
            status == null -> SManga.UNKNOWN
            ongoingWords.any { status.contains(it, ignoreCase = true) } -> SManga.ONGOING
            completedWords.any { status.contains(it, ignoreCase = true) } -> SManga.COMPLETED
            hiatusWords.any { status.contains(it, ignoreCase = true) } -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private fun parseChapterList(document: Document): List<SChapter> = document.select("ul.fx-chap-list li.fx-chap-item").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
            name = element.select("a").text()
            date_upload = dateFormat.tryParse(element.selectFirst("span.fx-chap-item__date")?.text())
        }
    }

    private fun DateTimeFormatter.tryParse(date: String?): Long = runCatching {
        LocalDate.parse(date!!, this)
            .atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh"))
            .toInstant()
            .toEpochMilli()
    }.getOrDefault(0L)

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        client.get(getChapterUrl(chapter)).use { response ->
            val document = response.asJsoup()
            return document.select(".content_detail img")
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
