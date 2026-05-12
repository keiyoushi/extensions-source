package eu.kanade.tachiyomi.extension.en.manhwa18

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class Manhwa18 : HttpSource() {

    override val name = "Manhwa18"

    override val baseUrl = "https://manhwa18.com"

    override val lang = "en"

    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/tim-kiem?sort=top&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".thumb-item-flow").map { element ->
            SManga.create().apply {
                val a = element.selectFirst("a")!!
                setUrlWithoutDomain(a.attr("abs:href"))
                title = element.selectFirst(".series-title a")!!.text()
                thumbnail_url = element.selectFirst(".lazy-bg")?.attr("data-bg")
                    ?: element.selectFirst(".img-in-ratio")?.attr("style")?.substringAfter("url('")?.substringBefore("')")
            }
        }
        val hasNextPage = document.selectFirst(".pagination_wrap a.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/tim-kiem?sort=update&page=$page", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder()

        if (query.isNotEmpty()) {
            url.addQueryParameter("q", query)
        }

        val sortFilter = filters.firstInstanceOrNull<SortFilter>()
        val statusFilter = filters.firstInstanceOrNull<StatusFilter>()
        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()

        url.addQueryParameter("sort", sortFilter?.selectedValue() ?: "update")

        if (statusFilter != null && statusFilter.state != 0) {
            url.addQueryParameter("status", statusFilter.selectedValue())
        }

        if (genreFilter != null) {
            val included = genreFilter.state.filter { it.state }.joinToString(",") { it.id }
            if (included.isNotEmpty()) {
                url.addQueryParameter("accept_genres", included)
            }
        }

        url.addQueryParameter("page", page.toString())

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // ============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst(".series-name a")!!.text()
            thumbnail_url = document.selectFirst(".series-cover .img-in-ratio")?.attr("style")?.substringAfter("url('")?.substringBefore("')")
            description = document.selectFirst(".summary-content")?.text()

            val infoItems = document.select(".series-information .info-item")
            for (item in infoItems) {
                val name = item.selectFirst(".info-name")?.text() ?: continue
                val value = item.selectFirst(".info-value")?.text() ?: continue

                when {
                    name.contains("Author", true) -> author = value
                    name.contains("Genre", true) -> genre = item.select(".info-value a").joinToString { it.text() }
                    name.contains("Status", true) -> status = when (value.lowercase()) {
                        "ongoing" -> SManga.ONGOING
                        "completed" -> SManga.COMPLETED
                        "on hold" -> SManga.ON_HIATUS
                        else -> SManga.UNKNOWN
                    }
                }
            }

            // Fallback for author field if not strictly displayed in info items
            if (author.isNullOrEmpty()) {
                author = document.selectFirst(".fantrans-value a")?.text()
            }
        }
    }

    // ============================= Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("ul.list-chapters a").map { a ->
            SChapter.create().apply {
                setUrlWithoutDomain(a.attr("abs:href"))
                name = a.selectFirst(".chapter-name")!!.text()

                val timeStr = a.selectFirst(".chapter-time")?.text()?.substringAfter("-")?.trim()
                date_upload = dateFormat.tryParse(timeStr)
            }
        }
    }

    // =============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("#chapter-content img.lazy").mapIndexed { i, img ->
            Page(i, imageUrl = img.attr("abs:data-src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList() = FilterList(
        SortFilter(),
        StatusFilter(),
        GenreFilter(genreList),
    )
}
