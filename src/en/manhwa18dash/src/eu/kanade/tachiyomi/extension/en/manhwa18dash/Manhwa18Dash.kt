package eu.kanade.tachiyomi.extension.en.manhwa18dash

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
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Manhwa18Dash : KeiSource() {

    override val supportsFilterFetching = true

    override val supportsRelatedMangas = true

    override suspend fun getPopularManga(page: Int): MangasPage = seriesPage(sort = "popular")

    override suspend fun getLatestUpdates(page: Int): MangasPage = seriesPage(sort = "latest")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = seriesPage(
        query = query,
        sort = filters.firstInstanceOrNull<SortFilter>()?.value ?: "popular",
        status = filters.firstInstanceOrNull<StatusFilter>()?.value,
        genres = filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty().filter { it.state }.map { genreKey(it.name) },
    )

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val onSite = url.host.removePrefix("www.") == baseUrl.toHttpUrl().host
        val segments = url.pathSegments
        return when {
            onSite && segments[0] == "manhwa" -> seriesPage(
                query = url.queryParameter("q").orEmpty(),
                sort = url.queryParameter("sort") ?: "latest",
                status = url.queryParameter("status")?.lowercase()?.takeUnless { it == "all" },
                genres = url.queryParameter("genres")?.split(',').orEmpty().map(::genreKey),
            )
            onSite && segments[0] == "genre" && segments.size > 1 -> seriesPage(sort = "latest", genres = listOf(genreKey(segments[1])))
            else -> super.getMangasByUrl(url, page)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        val slug = when (url.pathSegments[0]) {
            "manga" -> url.pathSegments.getOrNull(1)
            "reader.php" -> url.queryParameter("seri")
            else -> null
        }
        if (slug.isNullOrEmpty()) return null

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
        return SMangaUpdate(document.toSManga(manga.url), document.toChapters(manga.url))
    }

    // The chapter page has every image: the site's page list API returns nothing for chapters like 53.5
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val folder = chapter.url.substringAfterLast("/chapter-")
        val images = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
            .select("#pages-container img.manga-page")
            .map { it.absUrl("data-src").ifEmpty { it.absUrl("src") } }
        // When chapters 43 and 43.5 both exist, the site only opens 43
        if (images.firstOrNull()?.toHttpUrl()?.pathSegments?.getOrNull(2) != folder) return emptyList()

        return images.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        .select("#recommendation-slider a.manga-card")
        .map { card ->
            SManga.create().apply {
                setUrlWithoutDomain(card.absUrl("href"))
                title = card.selectFirst(".manga-title")!!.text()
                thumbnail_url = card.selectFirst("img")?.absUrl("src")
            }
        }

    // The site builds its genre list from the series it has
    override suspend fun fetchFilterData(): JsonElement = fetchSeries()
        .flatMap { it.genres }
        .map { it.trim() }
        .distinctBy(::genreKey)
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<String>>())

    // Like the site's catalogue: the whole list at once, filtered and sorted here
    private suspend fun seriesPage(
        query: String = "",
        sort: String,
        status: String? = null,
        genres: List<String> = emptyList(),
    ): MangasPage {
        val term = query.trim().lowercase()
        val series = fetchSeries().filter { it.matches(term, status, genres) }
        val sorted = if (sort == "newest") {
            series
        } else {
            val data = client.get("$baseUrl/upload.php?fetch=all_data", Fetch.XHR.from("$baseUrl/manhwa")).parseAs<Map<String, SeriesDataDto>>()
            if (sort == "popular") {
                series.sortedByDescending { data[it.slug]?.views ?: 0 }
            } else {
                series.sortedWith(
                    compareByDescending<SeriesDto> { data[it.slug]?.lastUpdate.orEmpty() }
                        .thenByDescending { data[it.slug]?.chapterCount ?: 0 },
                )
            }
        }
        return MangasPage(sorted.map { it.toSManga(cdnUrl) }, false)
    }

    // In the site's order: newest series first
    private suspend fun fetchSeries(): List<SeriesDto> = client.get("$baseUrl/data.json", Fetch.XHR.from("$baseUrl/manhwa")).parseAs<SeriesListDto>().series

    private fun Document.toSManga(mangaUrl: String) = SManga.create().apply {
        url = mangaUrl
        title = selectFirst("#mangaTitle")!!.text()
        thumbnail_url = selectFirst("#mangaCoverImg")?.absUrl("src")
        // The site shows this when a series has no author
        author = selectFirst("#mangaAuthor")?.textOrNull()?.takeUnless { it == "Unknown Author" }
        genre = select("#mangaGenres a.tag").joinToString { it.text() }.ifEmpty { null }
        // Status and release year follow the genres
        val tags = select("#mangaGenres span.tag").map { it.text().lowercase() }
        status = when {
            "ongoing" in tags -> SManga.ONGOING
            "completed" in tags -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        val votes = selectFirst("#ratingVotes")?.text()?.toIntOrNull() ?: 0
        val rating = selectFirst("#ratingScoreBadge")?.text()?.toDoubleOrNull()?.takeIf { votes > 0 }
        description = MarkdownDescription.Builder()
            // Members vote from one to five stars, although the site labels the average out of 10
            .rating(rating, max = 5, votes = votes)
            .info(tags.firstOrNull { it.length == 4 && it.all(Char::isDigit) })
            .description(selectFirst("#mangaSynopsis"))
            .altTitles(altTitles(selectFirst("#altTitlesText"), title))
            .build()
    }

    private fun altTitles(element: Element?, title: String): List<String> {
        element ?: return emptyList()
        val hidden = element.selectFirst("#altTitlesHidden")?.text().orEmpty()
        return (element.ownText() + " • " + hidden).split('•')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != title }
            .distinct()
    }

    // The folder name is the chapter's id: the site links 43 and 43.5 to the same page
    private fun Document.toChapters(mangaUrl: String) = select("#chapter-list a.chapter-card").map { card ->
        SChapter.create().apply {
            url = "$mangaUrl/chapter-${card.attrOrNull("data-ch")!!}"
            name = card.selectFirst(".ch-num")!!.text()
            date_upload = DATE_FORMAT.tryParseDateTime(card.attr("data-added"), ZoneOffset.UTC)
        }
    }

    private val cdnUrl get() = CDN_URL.toHttpUrl()

    companion object {
        private const val CDN_URL = "https://cdn.manhwa18dash.com"
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
    }
}
