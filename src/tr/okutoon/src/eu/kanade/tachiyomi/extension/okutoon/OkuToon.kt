package eu.kanade.tachiyomi.extension.tr.okutoon

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class OkuToon : KeiSource() {

    private val dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr"))

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList("$baseUrl/tur?sira=popular&sayfa=$page".toHttpUrl())

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList("$baseUrl/tur?sira=updated&sayfa=$page".toHttpUrl())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
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

        return parseMangaList(url.build())
    }

    private suspend fun parseMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url).asJsoup()
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

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        return SMangaUpdate(mangaDetailsParse(document), chapterListParse(document))
    }

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst(".series-detail-title")!!.text()
        author = document.selectFirst(".series-detail-author")?.text()?.takeIf { it != "Bilinmiyor" }
        description = document.selectFirst("[data-series-description-content]")?.text()
        genre = document.select(".series-detail-genres .tag").joinToString { it.text() }
        status = parseStatus(document.selectFirst(".series-detail-meta .badge-completed, .series-detail-meta .badge-ongoing")?.text())
        thumbnail_url = document.selectFirst(".series-detail-cover img")?.attr("abs:src")
    }

    private fun parseStatus(status: String?) = when (status?.trim()) {
        "Devam Ediyor" -> SManga.ONGOING
        "Tamamlandı" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun chapterListParse(document: Document): List<SChapter> = document.select("a.chapter-item").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.attr("abs:href"))
            name = element.selectFirst(".chapter-title")?.text() ?: element.text()
            date_upload = dateFormat.tryParseDate(element.selectFirst(".chapter-date")?.text())
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("#readerPages img.reader-page").mapIndexed { index, img ->
            Page(index, imageUrl = img.attr("abs:src"))
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        StatusFilter(),
        GenreFilter(getGenreList()),
    )
}
