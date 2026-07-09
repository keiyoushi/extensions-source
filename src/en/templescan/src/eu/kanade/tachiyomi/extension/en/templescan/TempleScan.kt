package eu.kanade.tachiyomi.extension.en.templescan

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.lib.randomua.addRandomUAPreference
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.network.rateLimit
import keiyoushi.utils.extractNextJs
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import rx.Observable
import kotlin.math.min

@Source
abstract class TempleScan :
    HttpSource(),
    ConfigurableSource {

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .set("referer", "$baseUrl/")
        .set("origin", baseUrl)
        .setRandomUserAgent()

    private val rscHeaders = headersBuilder()
        .set("rsc", "1")
        .build()

    override val client = network.client.newBuilder()
        .rateLimit(1)
        .build()

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = fetchSearchManga(page, "", OrderFilter.POPULAR)

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> = fetchSearchManga(page, "", OrderFilter.LATEST)

    private lateinit var seriesCache: List<BrowseSeries>

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (page == 1) {
            client.newCall(searchMangaRequest(page, query, filters))
                .execute()
                .use(::parseSearchResponse)
        }
        return Observable.just(parseDirectory(page, query, filters))
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/comics", rscHeaders)

    private fun parseSearchResponse(response: Response) {
        seriesCache = response.extractNextJs<List<BrowseSeries>>()!!
    }

    private fun parseDirectory(page: Int, query: String, filters: FilterList): MangasPage {
        val status = filters.get<StatusFilter>()?.selected
        val mangaList = seriesCache.filter { series ->

            val queryFilter = query.isBlank() ||
                series.title.contains(query, ignoreCase = true) ||
                series.alternativeNames?.contains(query, ignoreCase = true) == true

            val statusFilter = status == null || series.status == status

            queryFilter && statusFilter
        }.let {
            val order = filters.get<OrderFilter>()?.selected

            when (order) {
                "updated" -> it.sortedByDescending { series -> series.updated }
                "created" -> it.sortedByDescending { series -> series.created }
                "views" -> it.sortedByDescending { series -> series.views }
                else -> it
            }
        }

        return MangasPage(
            mangas = mangaList.subList((page - 1) * 20, min(page * 20, mangaList.size))
                .map { it.toSManga() },
            hasNextPage = page * 20 < mangaList.size,
        )
    }

    override fun getFilterList() = getFilters()

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(response: Response): SManga {
        val details = response.extractNextJs<SeriesDetails>()!!

        return SManga.create().apply {
            url = "/comic/${details.slug}"
            title = details.title
            thumbnail_url = details.thumbnail
            status = when (details.status) {
                "Ongoing" -> SManga.ONGOING
                "Hiatus" -> SManga.ON_HIATUS
                "Completed" -> SManga.COMPLETED
                "Canceled" -> SManga.CANCELLED
                "Dropped" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
            author = details.author
            artist = details.studio
            // Sometimes site adds #tags at the end of description
            // Site can use any word to indicate tags, I saw at least: "Tags:", "Keywords:", TAGS
            val cleanDescription = if (details.description?.contains("#") == true) {
                details.description.substringBefore("#").replace(LAST_WORD_REGEX, "").trim()
            } else {
                details.description.toString()
            }
            description = buildString {
                append(Jsoup.clean(cleanDescription, Safelist.none()))
                details.alternativeNames?.takeIf { it.isNotBlank() }?.let {
                    append("\n\n")
                    append("Alternative Name: $it\n")
                }
            }
            genre = buildList {
                add(details.badge)
                add(details.year)
                if (details.adult) {
                    add("Adult")
                }
                details.tags?.map { it.tag.name }?.let { addAll(it) }
                details.description?.takeIf { it.contains("#") }?.let { desc ->
                    addAll(TEXT_TAGS_REGEX.findAll(desc).map { it.groupValues[1] })
                }
            }.filterNotNull().joinToString()
        }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl${manga.url}", rscHeaders)

    // ============================== Chapters ==============================
    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl${manga.url}", rscHeaders)

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = response.extractNextJs<ChapterList>() ?: return emptyList()
        val mangaSlug = response.request.url.pathSegments.last()

        return chapters.seasons.flatMap { season ->
            season.chapters.filter {
                it.price == 0
            }.map { chapter ->
                SChapter.create().apply {
                    url = "/comic/$mangaSlug/${chapter.slug}"
                    name = buildString {
                        append(chapter.name)
                        if (!chapter.title.isNullOrBlank()) {
                            append(": ", chapter.title)
                        }
                    }
                    date_upload = chapter.created
                }
            }
        }
    }

    // =============================== Pages ================================
    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl${chapter.url}", rscHeaders)

    override fun pageListParse(response: Response): List<Page> {
        val data = response.extractNextJs<PagesList>() ?: return emptyList()
        return data.pages.mapIndexed { idx, url ->
            Page(idx, imageUrl = url)
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addRandomUAPreference()
    }

    private inline fun <reified T : Filter<*>> FilterList.get(): T? = filterIsInstance<T>().firstOrNull()

    override fun popularMangaRequest(page: Int) = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response) = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    companion object {
        private val TEXT_TAGS_REGEX = """(?i)#(\w+)""".toRegex()
        private val LAST_WORD_REGEX = """[\w\s]+:?\s*$""".toRegex()
    }
}
