package eu.kanade.tachiyomi.extension.vi.sanyteam

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.ownTextOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Source
abstract class SanyTeam : KeiSource() {

    // The site has no view ranking: the most commented series come first
    override suspend fun getPopularManga(page: Int) = getMangaList("manga", page, mapOf("orderby" to "comment_count"))

    // A series is modified when a chapter is added to it
    override suspend fun getLatestUpdates(page: Int) = getMangaList("manga", page, mapOf("orderby" to "modified"))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()
        val parameters = buildMap {
            if (query.isNotBlank()) put("s", query.trim())
            sort?.orderBy?.let {
                put("orderby", it)
                put("order", sort.order)
            }
        }
        return getMangaList("manga", page, parameters)
    }

    private suspend fun getMangaList(path: String, page: Int, parameters: Map<String, String> = emptyMap()): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addEncodedPathSegments("$path/")
            if (page > 1) addPathSegments("page/$page/")
            parameters.forEach { (name, value) -> addQueryParameter(name, value) }
        }.build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("div.animposx > a").map { link ->
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.selectFirst(".title")!!.text()
                thumbnail_url = link.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, document.selectFirst(".pagination a:has(.fa-caret-right)") != null)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(parseDetails(document, manga.url), parseChapters(document))
    }

    private fun parseDetails(document: Document, path: String) = SManga.create().apply {
        url = path
        title = document.selectFirst(".infox h1.entry-title")!!.text()
        thumbnail_url = document.selectFirst(".infoanime .thumb img")?.absUrl("src")
        author = document.info("Tác Giả")
        genre = (listOfNotNull(document.info("Type")) + document.select(".infox .genre-info a").map { it.text() })
            .distinct()
            .joinToString()
            .ifEmpty { null }
        status = when (document.info("Tình Trạng")?.lowercase()) {
            "đang làm" -> SManga.ONGOING
            "đã hoàn" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .description(document.selectFirst(".infox .desc .entry-content"))
            .altTitles(document.info("Tên Khác")?.split(", ").orEmpty())
            .build()
    }

    private fun parseChapters(document: Document): List<SChapter> {
        val group = document.info("Nhóm Dịch")
        return document.select("#chapter_list li").map { item ->
            val link = item.selectFirst(".lchx a")!!
            SChapter.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                name = link.text()
                // Side stories are named without their number, which the site keeps next to the name
                chapter_number = item.selectFirst(".eps chapter")?.text()?.let { NUMBER_REGEX.find(it)?.value?.toFloat() } ?: -1f
                date_upload = DATE_FORMAT.tryParseDate(item.selectFirst(".date")?.text(), ZONE)
                scanlator = group
            }
        }
    }

    private fun Document.info(label: String) = select(".spe span").firstOrNull { it.selectFirst("b")?.text() == label }?.ownTextOrNull()

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val path = url.sitePath()
        if (path?.size == 2 && path[0] == "genre") return getMangaList("genre/${path[1]}", page)
        return super.getMangasByUrl(url, page)
    }

    // Chapter pages sit at the site's root and link to their series
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val path = url.sitePath() ?: return null
        val seriesPath = when {
            path.size == 2 && path[0] == "manga" -> "/manga/${path[1]}/"
            path.size == 1 -> client.get("$baseUrl/${path[0]}/", Fetch.DOCUMENT).asJsoup()
                .selectFirst(".sbdbti a[href*=/manga/]")
                ?.absUrl("href")
                ?.toHttpUrl()
                ?.encodedPath
                ?: return null
            else -> return null
        }
        return getMangaUpdate(SManga.create().apply { this.url = seriesPath }, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    private fun HttpUrl.sitePath() = encodedPathSegments.filter(String::isNotEmpty)
        .takeIf { host.removePrefix("www.") == baseUrl.toHttpUrl().host }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("div.reader-area img")
        .mapIndexed { i, img -> Page(i, imageUrl = img.absUrl("src")) }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    companion object {
        private val NUMBER_REGEX = Regex("""^\d+(\.\d+)?""")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        private val ZONE = ZoneId.of("Asia/Ho_Chi_Minh")
    }
}
