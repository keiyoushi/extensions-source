package eu.kanade.tachiyomi.extension.all.d9comics

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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class D9Comics : KeiSource() {

    // The site writes Portuguese as "pt"
    private val siteLang get() = lang.substringBefore('-')

    // The whole catalogue is two small lists: series in the site's order, then the comics outside a series
    override suspend fun getPopularManga(page: Int) = MangasPage(fetchCatalogue().entries().map { it.first }, false)

    override suspend fun getLatestUpdates(page: Int) = MangasPage(fetchCatalogue().entries().sortedByDescending { it.second }.map { it.first }, false)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangas = fetchCatalogue().entries().map { it.first }.filter { it.title.contains(query.trim(), ignoreCase = true) }
        return MangasPage(mangas, false)
    }

    // Series links (/series/<id>.html?lang=es) and comic links (/comics-online-es/<slug>, /private/comics-online-es/<slug>)
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        val catalogue = fetchCatalogue()
        val segments = url.pathSegments.filter { it.isNotEmpty() }.dropWhile { it == "private" }
        val seriesId = when (segments.firstOrNull()) {
            "series" -> segments.getOrNull(1)?.removeSuffix(".html")?.takeIf { (url.queryParameter("lang") ?: siteLang) == siteLang }
            "comics-online-$siteLang" -> {
                val comic = catalogue.comics.firstOrNull { it.url.substringAfterLast('/') == segments.getOrNull(1) } ?: return null
                comic.seriesId?.takeIf { id -> catalogue.series.any { it.id == id } } ?: return comic.toSManga(baseUrl).apply { initialized = true }
            }
            else -> null
        } ?: return null
        if (catalogue.comics.none { it.seriesId == seriesId }) return null
        return catalogue.series.firstOrNull { it.id == seriesId }?.toSManga(baseUrl, siteLang)?.apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val catalogue = fetchCatalogue()
        if (!manga.url.startsWith("/series/")) {
            val comic = catalogue.comics.first { it.url == manga.url }
            return SMangaUpdate(comic.toSManga(baseUrl), listOf(comic.toSChapter()))
        }
        val id = manga.url.substringAfter("/series/").substringBefore(".html")
        val series = catalogue.series.first { it.id == id }
        val issues = catalogue.comics.filter { it.seriesId == id }.sortedByDescending { it.issueNumber }
        return SMangaUpdate(series.toSManga(baseUrl, siteLang), issues.map { it.toSChapter() })
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.url.startsWith("/private/")) return emptyList()
        val script = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup().selectFirst("script:containsData(window.DB=)")!!.data()
        return READER_DATA_REGEX.find(script)!!.groupValues[1].parseAs<ReaderComicDto>().pages
            // Older comics keep their pages on Behance
            .mapIndexed { index, path -> Page(index, imageUrl = baseUrl.toHttpUrl().resolve(path)!!.toString()) }
    }

    private class Catalogue(val series: List<SeriesDto>, val comics: List<ComicDto>)

    private suspend fun fetchCatalogue() = coroutineScope {
        val series = async { client.get("$baseUrl/api/series", Fetch.XHR).parseAs<SeriesListDto>().series }
        val comics = async { client.get("$baseUrl/api/comics", Fetch.XHR).parseAs<ComicListDto>().comics }
        Catalogue(series.await(), comics.await().filter { it.lang == siteLang && !it.hidden })
    }

    // Each entry with the date of its newest comic
    private fun Catalogue.entries(): List<Pair<SManga, String>> {
        val ids = series.map { it.id }.toSet()
        val inSeries = series.sortedBy { it.displayOrder }.mapNotNull { series ->
            val issues = comics.filter { it.seriesId == series.id }
            if (issues.isEmpty()) null else series.toSManga(baseUrl, siteLang) to issues.maxOf { it.date }
        }
        return inSeries + comics.filter { it.seriesId !in ids }.map { it.toSManga(baseUrl) to it.date }
    }

    companion object {
        private val READER_DATA_REGEX = Regex("""window\.DB=\{Comic:(.+)\};""")
    }
}
