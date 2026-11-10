package eu.kanade.tachiyomi.extension.es.spnmanga

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter

@Source
abstract class SpnManga : KeiSource() {

    override suspend fun getPopularManga(page: Int) = getBrowsePage(page) { addQueryParameter("sort", "popular") }

    override suspend fun getLatestUpdates(page: Int) = getBrowsePage(page) { addQueryParameter("sort", "latest") }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = getBrowsePage(page) {
        if (query.isNotBlank()) addQueryParameter("q", query.trim())
        filters.filterIsInstance<UriPartFilter>().forEach { it.addTo(this) }
    }

    private suspend fun getBrowsePage(page: Int, params: HttpUrl.Builder.() -> Unit): MangasPage {
        val url = "$baseUrl/browse/".toHttpUrl().newBuilder()
            .apply(params)
            .addQueryParameter("page", page.toString())
            .build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("article.grid-item").map { item ->
            SManga.create().apply {
                val poster = item.selectFirst("a.poster")!!
                setUrlWithoutDomain(poster.absUrl("href"))
                title = item.selectFirst(".grid-item__name")!!.text()
                thumbnail_url = poster.selectFirst("img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("nav.pagination a[aria-label='Next page'][href]") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val (section, id, slug) = url.pathSegments.takeIf { it.size >= 3 } ?: return null
        if (section != "leer") return null
        val path = "/leer/$id/$slug/"
        return mangaDetailsParse(client.get(baseUrl + path, Fetch.DOCUMENT).asJsoup()).apply { this.url = path }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val chapterList = document.select("#chapter-list a.chapter-row").map { row ->
            SChapter.create().apply {
                setUrlWithoutDomain(row.absUrl("href"))
                name = row.selectFirst(".chapter-row__name")!!.text()
                date_upload = DateTimeFormatter.ISO_LOCAL_DATE.tryParseDate(row.selectFirst("time")?.attr("datetime"))
            }
        }
        return SMangaUpdate(mangaDetailsParse(document), chapterList)
    }

    private fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val detail = document.selectFirst("section.detail")!!
        title = detail.selectFirst("h1.detail__title")!!.text()
        thumbnail_url = detail.selectFirst(".detail__cover img")?.absUrl("src")
        author = detail.metaValue("Story")
        artist = detail.metaValue("Art")
        // The type (Manga, Manhwa...) comes first in "Manga · Shōnen", or "Manhua ·" without a demographic
        val type = detail.selectFirst(".eyebrow")?.text()?.substringBefore("·")?.trim()
        genre = (listOfNotNull(type) + detail.select(".chips a.chip").map { it.text() }).joinToString().ifEmpty { null }
        status = when (detail.selectFirst(".detail__facts [data-status]")?.attr("data-status")) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "paused" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .description(detail.selectFirst(".detail__syn")?.text())
            .altTitles(detail.selectFirst(".detail__alt")?.text()?.split(" · ").orEmpty())
            .build()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.select("img[data-index]").mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("src"))
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        GenreFilter(),
        ThemeFilter(),
        TypeFilter(),
        StatusFilter(),
    )

    private fun Element.metaValue(label: String) = select(".meta-block > div")
        .firstOrNull { it.selectFirst(".k")?.text() == label }
        ?.selectFirst("span:not(.k)")
        ?.text()
}
