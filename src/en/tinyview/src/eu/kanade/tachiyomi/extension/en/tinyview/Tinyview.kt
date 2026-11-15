package eu.kanade.tachiyomi.extension.en.tinyview

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
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Clock

@Source
abstract class Tinyview : KeiSource() {

    // The site's feeds come from its API, which needs a Firebase token: only its directory of series is public
    override val supportsLatest = false

    // The site's pages read their comics from the JSON files of its CDN
    private val cdnUrl get() = "https://cdn.${baseUrl.toHttpUrl().host}"

    override suspend fun getPopularManga(page: Int) = MangasPage(getDirectory().map { it.toSManga(cdnUrl) }, false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val series = getDirectory().filter { it.matches(query.trim()) }
        return MangasPage(series.map { it.toSManga(cdnUrl) }, false)
    }

    // Series and comic links, which start with the series
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        val series = url.pathSegments.firstOrNull { it.isNotEmpty() } ?: return null
        return getDirectory().firstOrNull { it.path == "/$series" }?.toSManga(cdnUrl)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val comic = getComic(manga.url)
        val series = manga.url.removePrefix("/")
        val details = SManga.create().apply {
            url = manga.url
            title = comic.title
            description = MarkdownDescription.Builder()
                .description(comic.description?.asJsoup(baseUrl)?.body())
                .links(comic.links.map { (site, link) -> site.replaceFirstChar { it.uppercase() } to link })
                .build()
            thumbnail_url = comic.panels.firstOrNull { it.image != null && it.action == null }
                ?.let { imageUrl(manga.url, it.image!!) } ?: manga.thumbnail_url
        }
        val now = Clock.System.now().toEpochMilliseconds()
        val comics = comic.panels
            .filter { it.isComicOf(series) }
            // Scheduled comics are in the list before their date
            .filter { it.date() <= now }
            .distinctBy { it.path }
            .sortedByDescending { it.date() }
        // Titles are free text: comics are numbered in order, so that numbers in titles aren't taken for chapter numbers
        val chapterList = comics.mapIndexed { index, panel -> panel.toSChapter(comics.size - index) }

        return SMangaUpdate(details, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val comic = getComic(chapter.url)
        if (comic.showTo == SUBSCRIBERS_ONLY) return emptyList()
        // Bonus panels are for subscribers, and panels with an action link to other comics or sites
        return comic.panels
            .filter { it.image != null && it.action == null && it.template != "bonus" }
            .mapIndexed { index, panel -> Page(index, imageUrl = imageUrl(chapter.url, panel.image!!)) }
    }

    private suspend fun getDirectory() = getComic("/tinyview/comic-series-directory").panels.filter { it.action != null }

    private suspend fun getComic(path: String) = client.get("$cdnUrl$path/index.json", Fetch.XHR).parseAs<ComicResponseDto>().comics

    // Panel images are given relative to their comic's folder, from the root, or in full
    private fun imageUrl(path: String, image: String) = "$cdnUrl$path/".toHttpUrl().resolve(image)!!.toString()
}
