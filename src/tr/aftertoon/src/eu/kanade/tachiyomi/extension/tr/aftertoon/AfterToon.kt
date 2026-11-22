package eu.kanade.tachiyomi.extension.tr.aftertoon

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
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.extractNextJsRsc
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import java.text.Collator
import java.util.Locale

@Source
abstract class AfterToon : KeiSource() {

    private val rscHeaders: Headers
        get() = headersBuilder().set("RSC", "1").build()

    // The home page ranks the series by views
    override suspend fun getPopularManga(page: Int): MangasPage {
        val series = getRsc("/").extractNextJs<List<PopularSeriesDto>>()!!
        return MangasPage(series.sortedByDescending { it.viewCount }.map { it.toSManga() }, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(getCatalog().allSeries.map { it.toSManga() }, false)

    // The catalogue holds every series on one page, and the site searches, filters and sorts it in the browser
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val text = query.trim().lowercase()
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.selected
        val status = filters.firstInstanceOrNull<StatusFilter>()?.selected
        val series = getCatalog().allSeries.filter {
            it.title.lowercase().contains(text) && (genre == null || genre in it.genres) && (status == null || it.status == status)
        }
        val sorted = when (filters.firstInstanceOrNull<SortFilter>()?.selected) {
            "rating" -> series.sortedByDescending { it.rating ?: 0.0 }
            "title" -> series.sortedWith(compareBy(TITLE_COLLATOR) { it.title })
            else -> series
        }
        return MangasPage(sorted.map { it.toSManga() }, false)
    }

    // Catalogue search and genre links; series and chapter links go to getMangaByUrl
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (!url.isSite()) return MangasPage(emptyList(), false)
        val segments = url.pathSegments.filter(String::isNotEmpty)
        return when {
            segments == listOf("manga") -> getSearchMangaList(page, url.queryParameter("q").orEmpty(), FilterList())
            segments.firstOrNull() == "tur" -> {
                val catalog = getCatalog()
                val genre = catalog.genres.firstOrNull { it.slug == segments.getOrNull(1) } ?: return MangasPage(emptyList(), false)
                MangasPage(catalog.allSeries.filter { genre.name in it.genres }.map { it.toSManga() }, false)
            }
            else -> super.getMangasByUrl(url, page)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = url.pathSegments.getOrNull(1)
        if (!url.isSite() || url.pathSegments[0] != "manga" || slug.isNullOrEmpty()) return null
        val manga = SManga.create().apply { this.url = "/manga/$slug" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // The series page has the details and the chapters, but only the catalogue has the status
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val status = async {
            if (fetchDetails) {
                getCatalog().allSeries.firstOrNull { it.slug == manga.url.substringAfterLast('/') }?.status.toStatus()
            } else {
                manga.status
            }
        }
        val page = getRsc(manga.url).use { it.body.string() }
        val series = page.extractNextJsRsc<SeriesDto>()!!
        val author = page.extractNextJsRsc<JsonLdScriptDto> { it is JsonObject && "ComicSeries" in it["__html"]?.stringOrNull.orEmpty() }?.author()
        SMangaUpdate(series.toSManga(author, status.await()), series.chapters.map { it.toSChapter(series.slug) })
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = getRsc(chapter.url).extractNextJs<ChapterPagesDto>()!!.imageUrls()
        .mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = getCatalog().genres.map { it.name }.toJsonElement()

    override fun getFilterList(data: JsonElement?) = getFilters(data?.parseAs<List<String>>())

    private suspend fun getCatalog() = getRsc("/manga").extractNextJs<CatalogDto>()!!

    // Without an empty _rsc parameter, the site answers each RSC request with a redirect to the same URL with one
    private suspend fun getRsc(path: String): Response = client.get(
        baseUrl.toHttpUrl().resolve(path)!!.newBuilder().addQueryParameter("_rsc", null).build(),
        rscHeaders,
        Fetch.XHR,
    )

    private fun HttpUrl.isSite() = host.removePrefix("www.") == baseUrl.toHttpUrl().host

    private fun String?.toStatus() = when (this) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    companion object {
        private val TITLE_COLLATOR = Collator.getInstance(Locale.forLanguageTag("tr"))
    }
}
