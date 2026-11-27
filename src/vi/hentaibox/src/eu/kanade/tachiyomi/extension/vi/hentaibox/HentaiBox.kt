package eu.kanade.tachiyomi.extension.vi.hentaibox

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
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class HentaiBox : KeiSource() {

    override val supportsFilterFetching = true

    override suspend fun getPopularManga(page: Int) = getMangaList(listUrl(page, sort = "views"))

    override suspend fun getLatestUpdates(page: Int) = getMangaList(listUrl(page))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder()
                .addQueryParameter("q", query.trim())
                .apply { if (page > 1) addQueryParameter("page", page.toString()) }
                .build()
            return getMangaList(url)
        }
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.value
        val sort = filters.firstInstanceOrNull<SortFilter>()?.value
        val status = filters.firstInstanceOrNull<StatusFilter>()?.value
        return getMangaList(listUrl(page, genre, sort, status.takeIf { genre == null }))
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val isList = segments == listOf("danh-sach") || segments == listOf("tim-kiem") || (segments.size == 2 && segments[0] == "the-loai")
        if (url.host != baseUrl.toHttpUrl().host || !isList) return super.getMangasByUrl(url, page)
        val listUrl = baseUrl.toHttpUrl().newBuilder()
            .encodedPath(url.encodedPath)
            .encodedQuery(url.encodedQuery)
            .setQueryParameter("page", page.toString())
            .build()
        return getMangaList(listUrl)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments
        if (url.host != baseUrl.toHttpUrl().host || segments.size < 2 || segments[0] != "truyen" || segments[1].isEmpty()) return null
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
        .select(".reader figure.page img")
        .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/danh-sach", Fetch.DOCUMENT).asJsoup()
        .select("a[href^=/the-loai/]")
        .map { it.text() to it.attr("href").removePrefix("/the-loai/") }
        .filter { it.first.isNotEmpty() && it.second.isNotEmpty() }
        .distinctBy { it.second }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<Pair<String, String>>>())

    private fun listUrl(page: Int, genre: String? = null, sort: String? = null, status: String? = null) = baseUrl.toHttpUrl().newBuilder().apply {
        if (genre != null) addPathSegment("the-loai").addPathSegment(genre) else addPathSegment("danh-sach")
        sort?.let { addQueryParameter("sort", it) }
        status?.let { addQueryParameter("status", it) }
        if (page > 1) addQueryParameter("page", page.toString())
    }.build()

    private suspend fun getMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".cards .card").map { it.toSManga() }
        return MangasPage(mangas, document.selectFirst(".pager a[rel=next]") != null)
    }

    private fun Element.toSManga() = SManga.create().apply {
        val link = selectFirst(".card-body h3 a")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = link.text()
        thumbnail_url = selectFirst(".card-img img")?.absUrl("src")
    }

    private fun Document.toSManga(mangaUrl: String) = SManga.create().apply {
        url = mangaUrl
        title = selectFirst(".detail-head h1")!!.text()
        thumbnail_url = selectFirst(".detail-cover img")?.absUrl("src")
        author = fact("Tác giả")?.takeUnless { it == UNKNOWN_AUTHOR }
        genre = select(".detail-head a.chip").joinToString { it.text() }.ifEmpty { null }
        status = when (selectFirst(".detail-head .pill")?.text()?.lowercase()) {
            "đang tiến hành" -> SManga.ONGOING
            "hoàn thành" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .description(selectFirst(".detail-body .desc")?.text())
            .build()
    }

    private fun Document.fact(label: String) = selectFirst(".detail-head .facts li:has(span:containsOwn($label)) b")?.textOrNull()

    private fun Document.toSChapters(): List<SChapter> = select("ul.chapters li[data-n]").map { item ->
        SChapter.create().apply {
            val link = item.selectFirst("a")!!
            setUrlWithoutDomain(link.absUrl("href"))
            name = link.selectFirst(".ch-name")!!.text()
            chapter_number = item.attr("data-n").toFloatOrNull() ?: -1f
            date_upload = parseDate(item.selectFirst(".ch-meta")?.ownText())
        }
    }

    private fun parseDate(text: String?): Long {
        text ?: return 0L
        RELATIVE_DATE_REGEX.find(text)?.let { match ->
            val amount = match.groupValues[1].toLong()
            val unit = when (match.groupValues[2]) {
                "giây" -> 1.seconds
                "phút" -> 1.minutes
                "giờ" -> 1.hours
                "ngày" -> 1.days
                "tuần" -> 7.days
                "tháng" -> 30.days
                else -> 365.days
            }
            return (Clock.System.now() - unit * amount.toInt()).toEpochMilliseconds()
        }
        return DATE_FORMAT.tryParseDate(text.trim(), ZONE)
    }

    companion object {
        private const val UNKNOWN_AUTHOR = "Đang cập nhật"
        private val RELATIVE_DATE_REGEX = Regex("""(\d+)\s*(giây|phút|giờ|ngày|tuần|tháng|năm)""")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        private val ZONE = ZoneId.of("Asia/Ho_Chi_Minh")
    }
}
