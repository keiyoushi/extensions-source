package eu.kanade.tachiyomi.extension.en.zipcomic

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
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class ZipComic : KeiSource() {

    // No ranking on the site: Popular is its list of ongoing series by last update, which the home page starts
    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList("$baseUrl/status/ongoing?p=$page".toHttpUrl())

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val params = filters.filterIsInstance<QueryFilter>().mapNotNull { filter -> filter.value?.let { filter.param to it } }
        // A search without keyword or filter shows the home page
        if (query.isBlank() && params.isEmpty()) return getPopularManga(page)
        val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("kwd", query.trim())
            params.forEach { (name, value) -> addQueryParameter(name, value) }
            addQueryParameter("p", page.toString())
        }.build()
        return getMangaList(url)
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (!url.isSiteUrl() || url.pathSegments.first() !in LIST_PATHS) return super.getMangasByUrl(url, page)
        val listUrl = url.newBuilder()
            .scheme("https")
            .host(baseUrl.toHttpUrl().host)
            .setQueryParameter("p", page.toString())
            .build()
        return getMangaList(listUrl)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = url.pathSegments.singleOrNull()?.takeIf { url.isSiteUrl() && it.isNotEmpty() } ?: return null
        var pageUrl = baseUrl.toHttpUrl().newBuilder().addPathSegment(slug).build()
        var document = client.get(pageUrl, Fetch.DOCUMENT).asJsoup()
        // An issue page links its series in the breadcrumb
        if (document.selectFirst("#images") != null) {
            pageUrl = document.selectFirst("ol.breadcrumb li:nth-last-child(2) a")?.absUrl("href")?.toHttpUrl() ?: return null
            document = client.get(pageUrl, Fetch.DOCUMENT).asJsoup()
        }
        // Other pages, unknown ones included, answer 200 without series details
        if (document.selectFirst(".block-content strong:containsOwn(Status:)") == null) return null
        return document.toSManga(pageUrl.encodedPath).apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(document.toSManga(manga.url), document.toSChapters())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("#images img")
        .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    private suspend fun getMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".block-content .row.push-50-t:has(h3 > a)").map { it.toSManga() }
        // Short pagers have no "Next" link, only page numbers
        return MangasPage(mangas, document.selectFirst("ul.pagination li.active + li a") != null)
    }

    private fun Element.toSManga() = SManga.create().apply {
        val link = selectFirst("h3 > a")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = link.text()
        thumbnail_url = selectFirst("img")?.absUrl("src")
    }

    private fun Document.toSManga(mangaUrl: String) = SManga.create().apply {
        val info = selectFirst(".block-content .col-md-9:has(> h1)")!!
        url = mangaUrl
        title = info.selectFirst("h1")!!.text()
        thumbnail_url = selectFirst(".block-content .col-md-3 > img")?.absUrl("src")
        author = info.links("Author:")
        artist = info.links("Artis:")
        genre = info.links("Genre:")
        status = when (info.field("Status:")?.text()?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .description(info.selectFirst("> div.push-10-t:not(:has(strong))"))
            .altTitles(listOfNotNull(info.field("Other names:")?.textOrNull()))
            .build()
    }

    // The value next to a label such as "Status:"
    private fun Element.field(label: String): Element? = selectFirst("strong:containsOwn($label) + *")

    private fun Element.links(label: String): String? = selectFirst("strong:containsOwn($label)")?.parent()
        ?.select("a")
        ?.joinToString { it.text() }
        ?.ifEmpty { null }

    // The table lists the oldest issue first
    private fun Document.toSChapters(): List<SChapter> = select(".block-content table tr:has(td > a)").map { row ->
        SChapter.create().apply {
            val link = row.selectFirst("td > a")!!
            setUrlWithoutDomain(link.absUrl("href"))
            name = link.text()
            date_upload = parseRelativeDate(row.selectFirst("td:last-child")?.text())
        }
    }.reversed()

    // The site only says how long ago an issue came out, such as "2 years ago"
    private fun parseRelativeDate(text: String?): Long {
        val match = text?.let { RELATIVE_DATE.matchEntire(it) } ?: return 0L
        val amount = match.groupValues[1].toIntOrNull() ?: 1
        val unit = when (match.groupValues[2]) {
            "second" -> 1.seconds
            "minute" -> 1.minutes
            "hour" -> 1.hours
            "day" -> 1.days
            "week" -> 7.days
            "month" -> 30.days
            else -> 365.days
        }
        return (Clock.System.now() - unit * amount).toEpochMilliseconds()
    }

    private fun HttpUrl.isSiteUrl() = host.removePrefix("www.") == baseUrl.toHttpUrl().host.removePrefix("www.")

    companion object {
        private val LIST_PATHS = setOf("genre", "status", "author", "artis", "search")
        private val RELATIVE_DATE = Regex("""(\d+|an?) (second|minute|hour|day|week|month|year)s? ago""")
    }
}
