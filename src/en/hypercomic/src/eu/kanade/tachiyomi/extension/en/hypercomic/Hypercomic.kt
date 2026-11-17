package eu.kanade.tachiyomi.extension.en.hypercomic

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.booleanOrNull
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class Hypercomic : KeiSource() {

    // The site's own list requests send this header.
    private val ajaxHeaders: Headers
        get() = headers.newBuilder().set("X-Requested-With", "XMLHttpRequest").build()

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/Webtoon/searchlist", Fetch.DOCUMENT).asJsoup()
        return MangasPage(document.select("ul.search_list > li > a").map(::listItem), false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = browse(page, ALL_DAYS)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return browse(page, filters.firstInstance<DayFilter>().weekday)
        val body = FormBody.Builder().add("searchval", query.trim()).build()
        val document = client.post("$baseUrl/Webtoon/searchlist", headers, Fetch.DOCUMENT, body).asJsoup()
        // The site gives the first 20 results of a search, with no further page.
        return MangasPage(document.select("#searchcont > li > a").map(::listItem), false)
    }

    private suspend fun browse(page: Int, weekday: Int): MangasPage {
        val url = "$baseUrl/Webtoon".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("vtype", "ajax")
            .addQueryParameter("weekday", weekday.toString())
            .addQueryParameter("lang", "en")
            .build()
        val result = client.get(url, ajaxHeaders, Fetch.XHR).parseAs<BrowseDto>()
        // The page after the last one is a 500.
        return MangasPage(result.toSMangaList(), page < result.totalpage)
    }

    private fun listItem(link: Element) = SManga.create().apply {
        setUrlWithoutDomain(link.absUrl("href"))
        title = link.selectFirst(".title")!!.text()
        thumbnail_url = link.selectFirst("img")?.absUrl("src")
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.getOrNull(0) != "Webtoon" || url.pathSegments.getOrNull(1) !in SERIES_PATHS) return null
        val id = url.pathSegments.getOrNull(2)?.toIntOrNull() ?: return null
        val manga = SManga.create().apply { this.url = "/Webtoon/episode/$id/1/" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val id = manga.url.split('/')[3]
        val document = client.get("$baseUrl/Webtoon/episode/$id/1/", Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(
            manga = parseDetails(document, manga),
            chapters = if (fetchChapters) fetchChapterList(id, document) else chapters,
        )
    }

    private fun parseDetails(document: Document, manga: SManga) = SManga.create().apply {
        url = manga.url
        title = document.selectFirst(".contents_wrap .text-xl")!!.text()
        author = document.selectFirst(".contents_wrap p.font-light")?.text()
        genre = document.select(".contents_wrap a[href=#]").joinToString { it.text() }.ifEmpty { null }
        // The series page shows a wide banner: the lists' portrait cover is kept when there is one.
        thumbnail_url = manga.thumbnail_url ?: document.selectFirst(".contents_wrap img")?.absUrl("src")
        val info = document.selectFirst(".info_text")
        description = MarkdownDescription.Builder()
            .description(
                info?.children()
                    ?.takeWhile { it.text() != "Details" }
                    ?.filter { it.tagName() == "p" }
                    ?.joinToString("\n\n") { it.text() },
            )
            .field("Publisher", info?.select("p")?.firstOrNull { it.selectFirst("strong")?.text() == "Publisher" }?.ownText())
            .build()
    }

    private suspend fun fetchChapterList(id: String, firstPage: Document): List<SChapter> {
        val script = firstPage.select("script").first { it.data().contains("function goPage") }.data()
        val pageCount = TOTAL_PAGE_REGEX.find(script)!!.groupValues[1].toInt()
        val otherPages = coroutineScope {
            (2..pageCount).map { page ->
                async { client.get("$baseUrl/Webtoon/episode/$id/$page/2/asc?vtype=ajax", ajaxHeaders, Fetch.XHR).asJsoup() }
            }.awaitAll()
        }
        return (listOf(firstPage) + otherPages)
            .flatMap { it.select(".webtoon_list_type_06 a") }
            .map { it.toSChapter(id) }
            .reversed()
    }

    // Episodes the account can't read open the site's unlock dialog (an ad or CUBIC) instead of the reader.
    private fun Element.toSChapter(seriesId: String): SChapter {
        val onclick = attr("onclick")
        val episode = EPISODE_REGEX.find(onclick)!!.groupValues[1]
        val title = selectFirst(".title")!!.text()
        val locked = "webtoonpay" in onclick
        return SChapter.create().apply {
            url = "/Webtoon/episodeview/$seriesId/$episode/1/1/asc"
            name = if (locked) "🔒 $title" else title
            if (locked) memo = buildJsonObject { put("locked", true) }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.memo["locked"]?.booleanOrNull == true) return emptyList()
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.select(".webtoon_view img").mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("src"))
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    companion object {
        private const val ALL_DAYS = 7
        private val SERIES_PATHS = listOf("episode", "episodeview")
        private val EPISODE_REGEX = Regex("""(?:viewPage|webtoonpay)\('(\d+)'""")
        private val TOTAL_PAGE_REGEX = Regex("""totalPage\s*=\s*(\d+)""")
    }
}
