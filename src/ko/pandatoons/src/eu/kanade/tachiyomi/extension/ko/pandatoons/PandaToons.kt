package eu.kanade.tachiyomi.extension.ko.pandatoons

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class PandaToons : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getBrowse(page, listOf("sort" to "popular"))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getBrowse(page, listOf("sort" to "updated"))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            // The search gives at most 120 works on one page, and only the ratings asked for
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("search")
                .addQueryParameter("searched", "1")
                .addQueryParameter("q", query.trim())
                .apply { RATINGS.forEach { addQueryParameter("rating[]", it) } }
                .build()
            return MangasPage(client.get(url, Fetch.DOCUMENT).asJsoup().parseCards(), false)
        }
        val params = buildList {
            filters.firstInstanceOrNull<SortFilter>()?.value?.let { add("sort" to it) }
            filters.firstInstanceOrNull<RatingFilter>()?.value?.let { add("rating" to it) }
            filters.firstInstanceOrNull<GenreFilter>()?.value?.let { add("genre" to it) }
            filters.firstInstanceOrNull<StatusFilter>()?.value?.let { add("status" to it) }
        }
        return getBrowse(page, params)
    }

    private suspend fun getBrowse(page: Int, params: List<Pair<String, String>>): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("browse")
            params.forEach { (name, value) -> addQueryParameter(name, value) }
            addQueryParameter("page", page.toString())
        }.build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        // The pager shows "current / last"
        val (current, last) = document.selectFirst(".pager .current")?.text()
            ?.split('/')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.takeIf { it.size == 2 }
            ?: listOf(page, page)
        return MangasPage(document.parseCards(), current < last)
    }

    private fun Document.parseCards(): List<SManga> = select("main .grid a.card[href^=/manhwa/]").map { card ->
        SManga.create().apply {
            setUrlWithoutDomain(card.absUrl("href"))
            title = card.selectFirst(".title")!!.text()
            thumbnail_url = card.selectFirst("img.cover")?.absUrl("src")
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        val path = when (url.pathSegments.firstOrNull()) {
            "manhwa" -> url.encodedPath
            // A chapter's reader links back to its series
            "read" -> client.get(url, Fetch.DOCUMENT).asJsoup().selectFirst(".reader-toolbar a[href^=/manhwa/]")?.attr("href")
            else -> null
        } ?: return null
        val manga = SManga.create().apply { this.url = path }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(
            manga = parseDetails(manga, document.selectFirst(".detail")!!),
            chapters = parseChapters(document),
        )
    }

    private fun parseDetails(manga: SManga, detail: Element): SManga = manga.apply {
        title = detail.selectFirst("h1")!!.text()
        thumbnail_url = detail.selectFirst(".detail-cover img")?.absUrl("src")
        author = detail.people("작가")
        artist = detail.people("그림")
        genre = detail.select(".genre-tags .tag").joinToString { it.text() }.ifEmpty { null }
        status = when (detail.fact("상태")?.text()) {
            "연재중" -> SManga.ONGOING
            "완결" -> SManga.COMPLETED
            "휴재" -> SManga.ON_HIATUS
            "중단" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .info(detail.fact("유형")?.text(), detail.fact("등급")?.text())
            .description(detail.selectFirst(".synopsis"), keepNewlines = true)
            .altTitles(listOfNotNull(detail.selectFirst(".alt-title")?.textOrNull()), "다른 제목")
            .build()
    }

    private fun Element.fact(label: String): Element? = selectFirst("table.facts td.k:containsOwn($label) + td")

    private fun Element.people(label: String): String? = fact(label)?.select(".person-name")?.joinToString { it.text() }?.ifEmpty { null }

    // The site lists the chapters oldest first
    private fun parseChapters(document: Document): List<SChapter> = document.select("ul.chapters a.chapter-row").map { row ->
        SChapter.create().apply {
            setUrlWithoutDomain(row.absUrl("href"))
            name = row.selectFirst(".chapter-name")!!.text()
            date_upload = dateFormat.tryParseDate(row.selectFirst(".when")?.text(), KOREA_ZONE)
        }
    }.reversed()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        val pages = document.selectFirst("script:containsData(READER_PAGES)")?.data()
            ?.let { READER_PAGES.find(it)?.groupValues?.get(1) }
            ?: return emptyList()
        return pages.parseAs<List<String>>().mapIndexed { i, imageUrl -> Page(i, imageUrl = imageUrl) }
    }

    override fun getFilterList(data: JsonElement?) = getFilters()

    companion object {
        // 전체, 자극적, 성인 and 노골적
        private val RATINGS = listOf("0", "1", "2", "3")
        private val READER_PAGES = Regex("""READER_PAGES\s*=\s*(\[.*?])\s*;""")
        private val KOREA_ZONE = ZoneId.of("Asia/Seoul")
        private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
    }
}
