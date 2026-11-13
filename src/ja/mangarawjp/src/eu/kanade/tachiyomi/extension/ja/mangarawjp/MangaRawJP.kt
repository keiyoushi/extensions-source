package eu.kanade.tachiyomi.extension.ja.mangarawjp

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Source
abstract class MangaRawJP : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(listUrl(page, "views"))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(listUrl(page, "lastest"))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstance<SortFilter>().value
        val genre = filters.firstInstance<GenreFilter>().value
        val url = if (query.isBlank() && genre.isNotEmpty()) {
            baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("manga-list")
                .addPathSegment(genre)
                .addPathSegment(page.toString())
                .addQueryParameter("order_by", sort)
                .build()
        } else {
            listUrl(page, sort, query.trim())
        }
        return getMangaList(url)
    }

    private fun listUrl(page: Int, sort: String, query: String = ""): HttpUrl = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("list-manga")
        .addPathSegment(page.toString())
        .apply { if (query.isNotEmpty()) addQueryParameter("search", query) }
        .addQueryParameter("order_by", sort)
        .build()

    private suspend fun getMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".original .unit").map {
            SManga.create().apply {
                val link = it.selectFirst(".info > a")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = it.selectFirst(".poster img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, document.selectFirst(".pagination a[rel=next]") != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "manga") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = "/manga/$slug" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        manga.parseDetails(document)
        return SMangaUpdate(manga, parseChapterList(document))
    }

    private fun SManga.parseDetails(document: Document) {
        val info = document.selectFirst(".manga-detail .info")!!
        title = info.selectFirst("h1")!!.text()
        thumbnail_url = document.selectFirst(".manga-detail .poster img")?.absUrl("src")
        genre = document.select(".manga-detail .meta a[href*=/manga-list/]").joinToString { it.text() }.ifEmpty { null }
        status = when (info.selectFirst("> p")?.text()) {
            "放送中" -> SManga.ONGOING
            "完了" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .description(info.selectFirst(".description"))
            .altTitles(info.selectFirst("h6")?.text()?.split(",").orEmpty().map { it.trim() }.filter { it.isNotEmpty() && it != title }.distinct())
            .build()
    }

    private fun parseChapterList(document: Document): List<SChapter> = document.select(".m-list li.item a").map {
        SChapter.create().apply {
            setUrlWithoutDomain(it.absUrl("href"))
            name = it.selectFirst("span")!!.text()
            date_upload = DATE_FORMAT.tryParseDate(it.select("span").getOrNull(1)?.text(), ZoneId.of("Asia/Tokyo"))
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select(".ImageGallery img[data-original]")
        .mapIndexed { i, img -> Page(i, imageUrl = img.absUrl("data-original")) }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        Filter.Header("ジャンルはキーワード検索では無視されます"),
        GenreFilter(),
    )

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    }
}
