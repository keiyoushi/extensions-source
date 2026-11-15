package eu.kanade.tachiyomi.extension.en.readcomicsonlinelol

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
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

@Source
abstract class ReadComicsOnlineLol : KeiSource() {

    // The catalogue page holds every comic at once, most viewed first
    override suspend fun getPopularManga(page: Int) = MangasPage(getCatalogue().map { it.toSManga() }, false)

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/api/comics/browse".toHttpUrl().newBuilder()
            .addQueryParameter("tab", "latest")
            .addQueryParameter("offset", ((page - 1) * LATEST_LIMIT).toString())
            .addQueryParameter("limit", LATEST_LIMIT.toString())
            .build()
        val browse = client.get(url, Fetch.XHR).parseAs<BrowseDto>()
        return MangasPage(browse.comics.map { it.toSManga() }, browse.hasMore)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val publisher = filters.firstInstanceOrNull<PublisherFilter>()?.selected
        val status = filters.firstInstanceOrNull<StatusFilter>()?.selected
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.selected
        val sort = filters.firstInstanceOrNull<SortFilter>()?.selected
        val weekStart = (Clock.System.now() - 7.days).toEpochMilliseconds()
        val comics = getCatalogue()
            .filter { it.matches(query, publisher, status, genre, weekStart) }
            .sortedWith(ComicDto.comparator(sort))
        return MangasPage(comics.map { it.toSManga() }, false)
    }

    private suspend fun getCatalogue() = client.get("$baseUrl/comics", Fetch.DOCUMENT).asJsoup()
        .extractNextJs<CatalogueDto>()!!.comics

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return MangasPage(emptyList(), false)
        if (url.pathSegments[0] !in LIST_PAGES) return super.getMangasByUrl(url, page)
        val mangas = client.get(url, Fetch.DOCUMENT).asJsoup()
            .select("a[href^=\"/comic/\"]:has(img[alt$=\"$COVER_ALT\"])")
            .map { card ->
                val cover = card.selectFirst("img")!!
                SManga.create().apply {
                    setUrlWithoutDomain(card.absUrl("href"))
                    title = cover.attr("alt").removeSuffix(COVER_ALT)
                    thumbnail_url = cover.absUrl("src").replace("/covers-sm/", "/covers/")
                }
            }
            .distinctBy { it.url }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        if (segments[0] != "comic" || segments.size < 2) return null
        val manga = SManga.create().apply { this.url = "/comic/${segments[1]}" }
        return coroutineScope {
            val status = async { getStatus(manga.url) }
            // A missing comic answers 200 with an empty page
            val series = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup().series() ?: return@coroutineScope null
            series.toSManga(status.await() ?: SManga.UNKNOWN).apply {
                this.url = manga.url
                initialized = true
            }
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val status = if (fetchDetails) async { getStatus(manga.url) } else null
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val issues = document.extractNextJs<IssuesDto> { it is JsonObject && it["issues"] is JsonArray }!!.issues
        SMangaUpdate(
            document.series()!!.toSManga(status?.await() ?: manga.status),
            issues.map { it.toSChapter(manga.url) },
        )
    }

    // The comic's own page doesn't show its status, the catalogue does
    private suspend fun getStatus(comicUrl: String) = getCatalogue().find { it.url == comicUrl }?.mangaStatus()

    private fun Document.series() = select("script[type=application/ld+json]").map { it.data() }
        .find { "\"ComicSeries\"" in it }
        ?.parseAs<SeriesDto>()

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .extractNextJs<PagesDto>()
        ?.toPages()
        .orEmpty()

    override fun getFilterList(data: JsonElement?) = getFilters()

    companion object {
        private const val LATEST_LIMIT = 20
        private const val COVER_ALT = " comic cover"

        // Their comics come as cards whose cover is described as "<title> comic cover"
        private val LIST_PAGES = setOf("comics", "search", "genre", "publisher", "character")
    }
}
