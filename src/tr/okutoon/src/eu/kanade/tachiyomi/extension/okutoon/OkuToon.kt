package eu.kanade.tachiyomi.extension.tr.okutoon

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

class OkuToon : HttpSource() {

    override val name = "OkuToon"

    override val baseUrl = "https://okutoon.com"

    override val lang = "tr"

    override val supportsLatest = true

    private val dateFormat by lazy {
        SimpleDateFormat("d MMMM yyyy", Locale("tr"))
    }

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/tur?sira=popular&sayfa=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaList(response)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/tur?sira=updated&sayfa=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaList(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/tur".toHttpUrl().newBuilder()
        url.addQueryParameter("sayfa", page.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("q", query)
        }

        val sortFilter = filters.firstInstanceOrNull<SortFilter>()
        if (sortFilter != null) {
            url.addQueryParameter("sira", sortFilter.toUriPart())
        } else {
            url.addQueryParameter("sira", "updated")
        }

        val statusFilter = filters.firstInstanceOrNull<StatusFilter>()
        if (statusFilter != null && statusFilter.toUriPart().isNotEmpty()) {
            url.addQueryParameter("durum", statusFilter.toUriPart())
        }

        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
        genreFilter?.state?.filter { it.state }?.forEach {
            url.addQueryParameter("k[]", it.id)
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaList(response)

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".series-grid .series-card").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("abs:href"))
                title = element.selectFirst(".series-card-title")!!.text()
                thumbnail_url = element.selectFirst(".series-card-cover img")?.attr("abs:src")
            }
        }
        val hasNextPage = document.selectFirst("nav.pagination a.pagination-btn:contains(Sonraki)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst(".series-detail-title")!!.text()
            author = document.selectFirst(".series-detail-author")?.text()?.takeIf { it != "Bilinmiyor" }
            description = document.selectFirst("[data-series-description-content]")?.text()
            genre = document.select(".series-detail-genres .tag").joinToString { it.text() }
            status = parseStatus(document.selectFirst(".series-detail-meta .badge-completed, .series-detail-meta .badge-ongoing")?.text())
            thumbnail_url = document.selectFirst(".series-detail-cover img")?.attr("abs:src")
        }
    }

    private fun parseStatus(status: String?) = when (status?.trim()) {
        "Devam Ediyor" -> SManga.ONGOING
        "Tamamlandı" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("a.chapter-item").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.attr("abs:href"))
                name = element.selectFirst(".chapter-title")?.text() ?: element.text()
                date_upload = dateFormat.tryParse(element.selectFirst(".chapter-date")?.text())
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("#readerPages img.reader-page").mapIndexed { index, img ->
            Page(index, imageUrl = img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        SortFilter(),
        StatusFilter(),
        GenreFilter(getGenreList()),
    )
}
