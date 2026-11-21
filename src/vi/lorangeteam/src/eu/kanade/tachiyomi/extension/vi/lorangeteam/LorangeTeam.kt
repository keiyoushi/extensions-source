package eu.kanade.tachiyomi.extension.vi.lorangeteam

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
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
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
abstract class LorangeTeam : KeiSource() {

    // The home page's "recently updated" series are the ones last opened by anyone
    override val supportsLatest = false

    override val supportsFilterFetching = true

    // The catalogue has no sort option: it is always in alphabetical order
    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/truyen".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("search", query.trim())
            filters.filterIsInstance<QueryFilter>().forEach { filter ->
                filter.value?.let { addQueryParameter(filter.param, it) }
            }
            addQueryParameter("page", page.toString())
        }.build()
        return getMangaList(url)
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        // The catalogue with its filters, and genre pages, list series like the search does
        val isList = segments == listOf("truyen") || (segments.size == 2 && segments[0] == "the-loai")
        if (!url.isSiteUrl() || !isList) return super.getMangasByUrl(url, page)
        val listUrl = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments(segments.joinToString("/"))
            .encodedQuery(url.encodedQuery)
            .setQueryParameter("page", page.toString())
            .build()
        return getMangaList(listUrl)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (!url.isSiteUrl() || segments.size < 2 || segments[0] != "truyen") return null
        val manga = SManga.create().apply { this.url = "/truyen/${segments[1]}" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
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
        .select("main img[alt^=Page]")
        .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }

    // The novel genre only holds novels, which are left out
    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/truyen", Fetch.DOCUMENT).asJsoup()
        .select("select[name=genre] option")
        .mapNotNull { option ->
            val slug = option.attrOrNull("value")?.takeIf { it != "novel" } ?: return@mapNotNull null
            option.textOrNull()?.let { it to slug }
        }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<Pair<String, String>>>())

    private suspend fun getMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("a.manga-card").mapNotNull { it.toSManga() }
        // The next link drops the filters, but the last page has none
        return MangasPage(mangas, document.selectFirst("a[rel=next]") != null)
    }

    // Novels are text, with no chapters on the site
    private fun Element.toSManga(): SManga? {
        if (selectFirst("h3 + p")?.text() == "novel") return null
        return SManga.create().apply {
            setUrlWithoutDomain(absUrl("href"))
            title = selectFirst("h3")!!.text()
            thumbnail_url = selectFirst("img")?.absUrl("src")
        }
    }

    private fun Document.toSManga(mangaUrl: String) = SManga.create().apply {
        url = mangaUrl
        title = selectFirst("main h1")!!.text()
        thumbnail_url = selectFirst("main img")?.absUrl("src")
        genre = select("span:containsOwn(Thể loại) + div a.tag-pill")
            .mapNotNull { it.textOrNull() }
            .distinct()
            .joinToString()
            .ifEmpty { null }
        status = when (selectFirst("span:containsOwn(Trạng thái) + span")?.text()?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .description(selectFirst("section:has(h2:contains(Nội dung)) > p"))
            .build()
    }

    private fun Document.toSChapters(): List<SChapter> = select("section:has(h2:contains(Danh sách chương)) a[href*=/chuong/]").map { link ->
        SChapter.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            name = link.selectFirst("span.font-semibold")!!.text()
            date_upload = parseRelativeDate(link.selectFirst("span.text-xs")?.text())
        }
    }

    // The site only says how long ago a chapter came out, such as "1 year ago"
    private fun parseRelativeDate(text: String?): Long {
        val match = text?.let { RELATIVE_DATE.matchEntire(it) } ?: return 0L
        val amount = match.groupValues[1].toInt()
        val duration = when (match.groupValues[2]) {
            "second" -> amount.seconds
            "minute" -> amount.minutes
            "hour" -> amount.hours
            "day" -> amount.days
            "week" -> (amount * 7).days
            "month" -> (amount * 30).days
            else -> (amount * 365).days
        }
        return (Clock.System.now() - duration).toEpochMilliseconds()
    }

    private fun HttpUrl.isSiteUrl() = host.removePrefix("www.") == baseUrl.toHttpUrl().host.removePrefix("www.")

    companion object {
        private val RELATIVE_DATE = Regex("""(\d+) (second|minute|hour|day|week|month|year)s? ago""")
    }
}
