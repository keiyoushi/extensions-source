package eu.kanade.tachiyomi.extension.es.gcomics

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.text.Normalizer

@Source
abstract class Gcomics : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(catalog(webtoons = true, comics = true), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val type = filters.firstInstance<TypeFilter>()
        val words = query.simplified()
        val mangas = catalog(type.webtoons, type.comics).filter { it.title.simplified().contains(words) }
        return MangasPage(mangas, false)
    }

    // The whole catalogue fits in two pages: the webtoon series of the home page and the comics page
    private suspend fun catalog(webtoons: Boolean, comics: Boolean): List<SManga> = coroutineScope {
        val series = async { if (webtoons) entries(baseUrl, ".biblio a[href*=/serie/]") else emptyList() }
        val books = async { if (comics) entries("$baseUrl/comics/", "main .wp-caption a:has(img)") else emptyList() }
        series.await() + books.await()
    }

    private suspend fun entries(url: String, selector: String): List<SManga> = client.get(url, Fetch.DOCUMENT).asJsoup().select(selector).map { link ->
        SManga.create().apply {
            this.url = link.absUrl("href").toHttpUrl().encodedPath.trimEnd('/') + "/"
            // The lists only show covers: the address gives the title until the entry is opened
            title = this.url.trim('/').substringAfterLast('/').replace('-', ' ').replaceFirstChar(Char::uppercase)
            thumbnail_url = link.selectFirst("img")?.absUrl("src")?.let(::https)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter(String::isNotEmpty)
        val path = when {
            segments.size != 1 && segments.size != 2 -> return null
            // An episode links to its series
            segments[0] == "webtoon" -> client.get(url, Fetch.DOCUMENT).asJsoup()
                .selectFirst("main .index-ep a")!!.absUrl("href").toHttpUrl().encodedPath.trimEnd('/') + "/"
            segments[0] == "serie" || segments[0] == "comic" || segments.size == 1 -> "/${segments.joinToString("/")}/"
            else -> return null
        }
        return getMangaUpdate(SManga.create().apply { this.url = path }, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        if (manga.url.startsWith("/serie/")) {
            return SMangaUpdate(
                document.toSeries(manga),
                document.select("main a.ep-container").map { episode ->
                    SChapter.create().apply {
                        setUrlWithoutDomain(episode.absUrl("href"))
                        name = episode.selectFirst(".texto")!!.text()
                    }
                },
            )
        }

        // A comic is a single page with all its pages: a category, or a post
        val comic = SManga.create().apply {
            url = manga.url
            title = document.title().substringBeforeLast(" - Gcomics").trim()
            thumbnail_url = manga.thumbnail_url ?: document.selectFirst("main img")?.absUrl("src")?.let(::https)
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
        val chapter = SChapter.create().apply {
            url = manga.url
            name = comic.title
        }
        return SMangaUpdate(comic, listOf(chapter))
    }

    // The page's header section has a broken tag (`<div class="content"<section ...>`): no section to select
    private fun Document.toSeries(manga: SManga) = SManga.create().apply {
        url = manga.url
        title = selectFirst("main h2")!!.text()
        author = select("main a[href*=/autor/]").joinToString { it.text() }.ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(selectFirst("main .taxo-description"))
            .build()
        // The series page only has a wide banner; the lists have the cover
        thumbnail_url = manga.thumbnail_url ?: selectFirst("main img.taxo-banner")?.absUrl("src")?.let(::https)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        val images = if (chapter.url.startsWith("/webtoon/")) document.select("main img.webtoon-item") else document.select("main img")
        return images.mapIndexed { i, img -> Page(i, imageUrl = https(img.absUrl("src"))) }
    }

    override fun getFilterList(data: JsonElement?) = getFilters()

    // Older pages link their images over http, which redirects
    private fun https(url: String) = url.toHttpUrl().newBuilder().scheme("https").build().toString()

    private fun String.simplified() = Normalizer.normalize(this, Normalizer.Form.NFD).replace(MARKS, "").lowercase().trim()

    companion object {
        private val MARKS = Regex("""\p{M}+""")
    }
}
