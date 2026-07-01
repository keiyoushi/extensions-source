package eu.kanade.tachiyomi.extension.vi.meosss

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Source
abstract class MeoSSS : HttpSource() {

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(3)
        .build()

    private var genreList: List<Genre> = emptyList()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/dang-thinh-hanh/", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".manga-item-details").map { it.mangaFromPopularElement() }
        return MangasPage(mangas, hasNextPage = false)
    }

    private fun Element.mangaFromPopularElement(): SManga = SManga.create().apply {
        setUrlWithoutDomain(select("a[href*=/truyen/]").first()!!.absUrl("href"))
        title = select("h2.uk-text-bold a").first()!!.text()
        thumbnail_url = selectFirst("img")?.imgUrl()
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/moi-cap-nhat/".toHttpUrl().newBuilder()
        if (page > 1) {
            url.addPathSegment("page")
            url.addPathSegment(page.toString())
            url.addPathSegment("")
        }
        return GET(url.build(), headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".manga-item-grid").map { it.mangaFromGridElement() }
        val hasNextPage = document.select(".uk-pagination-next a").firstOrNull() != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun Element.mangaFromGridElement(): SManga = SManga.create().apply {
        setUrlWithoutDomain(select("a[href*=/truyen/]").first()!!.absUrl("href"))
        title = select("h2.uk-text-bold a").first()!!.text()
        thumbnail_url = select("img").firstOrNull()?.imgUrl()
    }

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/".toHttpUrl().newBuilder()
                .addQueryParameter("s", query)
            return GET(url.build(), headers)
        }

        val url = "$baseUrl/bo-loc-nang-cao/".toHttpUrl().newBuilder()
        filters.firstInstanceOrNull<GenreFilter>()?.state
            ?.filter { it.state }
            ?.forEach { url.addQueryParameter("genre[]", it.value) }
        filters.firstInstanceOrNull<StatusFilter>()?.let {
            val statusValue = STATUS_VALUES[it.state]
            if (statusValue.isNotEmpty()) url.addQueryParameter("status", statusValue)
        }
        filters.firstInstanceOrNull<AgeRatingFilter>()?.let {
            val ageValue = AGE_VALUES[it.state]
            if (ageValue.isNotEmpty()) url.addQueryParameter("age_rating", ageValue)
        }
        filters.firstInstanceOrNull<SortFilter>()?.let {
            url.addQueryParameter("sort", SORT_VALUES[it.state])
        }
        if (page > 1) {
            url.addPathSegment("page")
            url.addPathSegment(page.toString())
            url.addPathSegment("")
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val isFilterPage = document.select(".manga-filter-form").firstOrNull() != null

        if (isFilterPage) {
            val mangas = document.select(".manga-item-details").map { it.mangaFromPopularElement() }
            val hasNextPage = document.select("a:has(.uk-pagination-next)").firstOrNull()?.absUrl("href")?.isNotEmpty() == true
            return MangasPage(mangas, hasNextPage)
        }

        val mangas = document.select("article").mapNotNull { article ->
            val link = article.selectFirst("h2 a[href*=/truyen/]") ?: return@mapNotNull null
            val href = link.absUrl("href")
            // Exclude non-manga URLs
            if (!MANGA_URL_REGEX.containsMatchIn(href)) return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(href)
                title = link.text()
                thumbnail_url = article.selectFirst("img")?.imgUrl()
            }
        }
        val hasNextPage = document.select("a:has(.uk-pagination-next)").firstOrNull()?.absUrl("href")?.isNotEmpty() == true
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Filters ===============================

    private fun fetchGenres() {
        if (genreList.isNotEmpty()) return
        try {
            val document = client.newCall(GET("$baseUrl/bo-loc-nang-cao/", headers)).execute().asJsoup()
            genreList = document.select("input[name=genre[]]").map { input ->
                Genre(
                    name = input.attr("data-genre-name").ifEmpty { input.attr("value") },
                    value = input.attr("value"),
                )
            }.sortedBy { it.name }
        } catch (_: Exception) {
        }
    }

    override fun getFilterList(): FilterList {
        fetchGenres()
        return getFilters(genreList)
    }

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.select("#manga-title").first()!!.text()
            author = document.select(".manga-info-details a[href*=/tac-gia/]").firstOrNull()?.text()
            description = document.select("#manga-description").firstOrNull()?.text()
            genre = document.select(".manga-block a[href*=/the-loai/]").joinToString { it.text() }
            status = parseStatus(document.select("#manga-status").firstOrNull()?.text()) ?: 0
            thumbnail_url = document.select(".story-cover img").firstOrNull()?.imgUrl()
        }
    }

    private fun parseStatus(status: String?): Int? = when (status?.lowercase()) {
        "đang tiến hành", "đã theo kịp" -> SManga.ONGOING
        "trọn bộ" -> SManga.COMPLETED
        "kết thúc mùa" -> SManga.ON_HIATUS
        "nguồn tạm ngưng" -> SManga.ON_HIATUS
        "bị hủy" -> SManga.CANCELLED
        else -> null
    }

    // ============================== Chapters ===============================

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val mangaUrl = baseUrl + manga.url
        val chapters = mutableListOf<SChapter>()
        var page = 1

        while (true) {
            val doc = client.newCall(
                GET("$mangaUrl/chap/page/$page/", headers),
            ).execute().asJsoup()

            val pageChapters = doc.select(".chapter-item").map { chapterFromElement(it) }
            if (pageChapters.isEmpty()) break
            chapters.addAll(pageChapters)

            val hasNext = doc.select(".uk-pagination a[href*=/chap/page/]").any {
                it.text().toIntOrNull()?.let { num -> num > page } == true
            }
            if (!hasNext) break
            page++
        }

        chapters
    }

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val a = element.select("a.uk-link-toggle").first()!!
        setUrlWithoutDomain(a.absUrl("href"))
        name = element.select("h3").first()!!.text().substringAfterLast('\u2013').trim()
        date_upload = DATE_FORMAT.tryParse(a.select("time[datetime]").firstOrNull()?.attr("datetime"))
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("#chapter-content img").filterNot { it.closest(".init-manga-chapter-ad") != null }
            .mapIndexed { index, img ->
                Page(index, imageUrl = img.attr("data-original-src").ifEmpty { img.imgUrl() })
            }
    }

    // ============================== Utilities ===============================

    private fun Element.imgUrl(): String? = attr("src").ifEmpty { null }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }

        private val MANGA_URL_REGEX = Regex("/truyen/[a-z0-9]+(?:-[a-z0-9]+)*/$")

        private val STATUS_VALUES = arrayOf("", "ongoing", "season_end", "completed", "source_hiatus", "caught_up", "dropped")

        private val AGE_VALUES = arrayOf("", "all", "13+", "16+", "18+")

        private val SORT_VALUES = arrayOf("updated", "new", "old", "views", "views_day", "views_week", "views_month", "rating", "power", "follow")
    }
}
