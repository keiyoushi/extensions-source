package eu.kanade.tachiyomi.extension.id.omicaso

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Source
abstract class Omicaso : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage {
        val catalog = client.get("$baseUrl/api/manga.php?page=1&limit=1&mode=all").parseAs<CatalogDto>()
        return MangasPage(catalog.sections.trending.all.map { it.toSManga() }, false)
    }

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", FilterList())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/api/manga.php".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", PAGE_SIZE.toString())
            addQueryParameter("mode", filters.firstInstanceOrNull<ModeFilter>()?.toUriPart() ?: "all")
            addQueryParameter("sort", filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: "updated")
            filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()?.takeIf { it.isNotEmpty() }?.let {
                addQueryParameter("genre", it)
            }
            if (query.isNotBlank()) addQueryParameter("q", query.trim())
        }.build()

        val catalog = client.get(url).parseAs<CatalogDto>()
        return MangasPage(catalog.items.map { it.toSManga() }, catalog.hasMore)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.queryParameter("slug") ?: url.queryParameter("manga") ?: return null
        return parseMangaDetails(client.get(mangaPageUrl(slug)).asJsoup(), slug)
    }

    override fun getMangaUrl(manga: SManga) = mangaPageUrl(manga.slug())

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.slug()
        val document = client.get("${mangaPageUrl(slug)}&order=desc").asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document, slug),
            chapters = parseChapterList(document),
        )
    }

    private fun parseMangaDetails(document: Document, slug: String) = SManga.create().apply {
        url = slug
        title = document.selectFirst("h1")!!.text()
        thumbnail_url = document.selectFirst(".detail-poster img")?.attr("abs:src")
        author = document.statValue("Author")
        artist = document.statValue("Artist")
        genre = (document.select(".detail-rating-line a").map { it.text() } + listOfNotNull(document.statValue("Type")))
            .joinToString()
        status = when (document.statValue("Status")?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        description = buildString {
            document.selectFirst(".synopsis-box p")?.text()?.let { append(it) }
            document.selectFirst(".detail-alt-title")?.ownText()?.takeIf { it.isNotEmpty() }?.let {
                append("\n\nAlternative Name: ", it)
            }
        }.trim()
    }

    private fun Document.statValue(label: String) = selectFirst(".detail-stat-grid div:has(> span:containsOwn($label)) > strong")?.textOrNull()

    private fun parseChapterList(document: Document) = document.select("a.chapter-row").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.attr("abs:href"))
            name = element.selectFirst("strong")!!.text()
            date_upload = dateFormat.tryParseDateTime(element.selectFirst("time")?.attr("datetime"), zone)
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("img[data-reader-image]").mapIndexed { index, img ->
            Page(index, imageUrl = img.attr("abs:src"))
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData() = client.get(baseUrl).asJsoup()
        .select("select[data-genre-select] option[value~=.+]")
        .map { GenreDto(it.text().replace(genreCountRegex, ""), it.attr("value")) }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>(SortFilter(), ModeFilter())
        data?.parseAs<List<GenreDto>>()?.let { filters.add(GenreFilter(it)) }
        return FilterList(filters)
    }

    // old mangathemesia urls were /comik/<slug>/, the slug itself didn't change
    private fun SManga.slug() = url.trim('/').substringAfterLast('/')

    private fun mangaPageUrl(slug: String) = "$baseUrl/manga.php?slug=$slug"

    companion object {
        private const val PAGE_SIZE = 20
        private val genreCountRegex = Regex("""\s*\(\d+\)$""")
        private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val zone = ZoneId.of("Asia/Jakarta")
    }
}
