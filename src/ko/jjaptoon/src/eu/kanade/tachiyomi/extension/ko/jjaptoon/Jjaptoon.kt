package eu.kanade.tachiyomi.extension.ko.jjaptoon

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.textOrNull
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Jjaptoon : KeiSource() {

    // Old domains redirect to the current one
    override suspend fun fetchBaseUrls(client: OkHttpClient): List<String> = listOf(
        client.get(baseUrl, Fetch.DOCUMENT).use { it.request.url.toString() },
    )

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter(popular = true)))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query.trim())
                .build()
            // Search answers on one page, without pagination
            val document = client.get(url, Fetch.DOCUMENT).asJsoup()
            return MangasPage(document.select("a[href^=/comics/]:has(h3)").map(::mangaFromCard), false)
        }
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            filters.filterIsInstance<UriPartFilter>().forEach { filter ->
                filter.value?.let { addQueryParameter(filter.param, it) }
            }
            if (page > 1) addQueryParameter("comicsPage", page.toString())
        }.build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        // The home page also shows a carousel of featured series above the list
        val mangas = document.select("section[data-pagination-scroll-target] a[href^=/comics/]").map(::mangaFromCard)
        val hasNextPage = document.select("a[href*=comicsPage=]").any {
            it.absUrl("href").toHttpUrl().queryParameter("comicsPage") == (page + 1).toString()
        }
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromCard(card: Element) = SManga.create().apply {
        url = card.attr("href").substringAfterLast('/')
        title = card.selectFirst("h2, h3")!!.text()
        thumbnail_url = card.selectFirst("img")?.absUrl("src")
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return null
        val id = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: return null
        val seriesId = when (url.pathSegments.first()) {
            "comics" -> id
            "chapters" -> client.get(getChapterUrl(SChapter.create().apply { this.url = id }), Fetch.DOCUMENT).asJsoup()
                .selectFirst("[data-comic-id]")?.attr("data-comic-id") ?: return null
            else -> return null
        }
        val manga = SManga.create().apply { this.url = seriesId }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/comics/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(parseDetails(manga.url, document), parseChapters(document))
    }

    private fun parseDetails(id: String, document: Document): SManga = SManga.create().apply {
        val heading = document.selectFirst("h1")!!
        val header = heading.parent()!!
        // Badges: status, then the day of release, then genres
        val badges = header.selectFirst("> div")?.select("span")?.map { it.text() }.orEmpty()
        // Age rating, bookmark count and source platform
        val facts = header.selectFirst("p:contains(작가) + div")
        url = id
        title = heading.text()
        author = header.selectFirst("p:contains(작가) span")?.textOrNull()
        thumbnail_url = document.selectFirst("img[src*=/covers/]")?.absUrl("src")
        description = MarkdownDescription.Builder()
            .info(facts?.selectFirst("span")?.text(), facts?.selectFirst("span:contains(출처) strong")?.text())
            .description(document.selectFirst("h2:containsOwn(작품 소개) + p"))
            .build()
        genre = badges.drop(1).filterNot { it in DAYS }.joinToString().ifEmpty { null }
        status = when (badges.firstOrNull()) {
            "연재" -> SManga.ONGOING
            "완결" -> SManga.COMPLETED
            "휴재" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private fun parseChapters(document: Document): List<SChapter> = document.select("a[data-chapter-list-id]").map { link ->
        val lines = link.select("p")
        SChapter.create().apply {
            url = link.attr("data-chapter-list-id")
            name = lines.first()!!.text()
            date_upload = dateFormat.tryParseDateTime(lines.getOrNull(1)?.text(), KOREA_ZONE)
        }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/chapters/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        // The reader may show a watermarked copy of the original in data-original
        return document.select("div[data-reader-image] img").mapIndexed { i, img ->
            Page(i, imageUrl = img.absUrl("data-original").ifEmpty { img.absUrl("src") })
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    companion object {
        private val KOREA_ZONE = ZoneId.of("Asia/Seoul")
        private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
        private val DAYS = setOf("월", "화", "수", "목", "금", "토", "일")
    }
}
