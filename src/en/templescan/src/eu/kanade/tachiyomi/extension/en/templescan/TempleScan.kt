package eu.kanade.tachiyomi.extension.en.templescan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import kotlin.math.min

class TempleScan : HttpSource() {

    override val name = "Temple Scan"

    override val lang = "en"

    override val baseUrl = "https://templescan.net"

    override val supportsLatest = true

    override val versionId = 3

    override fun headersBuilder() = super.headersBuilder()
        .set("referer", "$baseUrl/")
        .set("origin", baseUrl)

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(1)
        .build()

    private val json: Json by injectLazy()

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return fetchSearchManga(page, "", OrderFilter.POPULAR)
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return fetchSearchManga(page, "", OrderFilter.LATEST)
    }

    private lateinit var seriesCache: List<BrowseSeries>

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (page == 1) {
            client.newCall(searchMangaRequest(page, query, filters))
                .execute()
                .use(::parseSearchResponse)
        }

        return Observable.just(parseDirectory(page, query, filters))
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/comics", headers)
    }

    private fun parseSearchResponse(response: Response) {
        val document = response.asJsoup()
        val script = document.selectFirst("script:containsData(allComics)")!!
            .data().unescape()

        with(script) {
            val raw = substringAfter("""allComics":""")
                .substringBeforeLast("}]")

            seriesCache = raw.parseAs()
        }
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

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val details = DETAILS_REGEX.find(document.body().outerHtml())!!.groupValues[1]
            .unescape()
            .parseAs<SeriesDetails>()

        val tags = mutableListOf<String>()

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
            description = buildString {
                document.selectFirst("div:has(> p:contains(description))")?.run {
                    selectFirst("p:contains(description)")?.remove()
                    selectFirst("div.mt-7:contains(Additional)")?.remove()
                    selectFirst("div.mt-7:contains(tag)")?.also {
                        tags += it.select("div.flex > p[class^=bg]").eachText()
                    }?.remove()
                    selectFirst("p:contains(tag), p:contains(genre)")?.let {
                        tags += it.text().substringAfter(":")
                            .split(",")
                            .map(String::trim)
                        // sometimes description <p> have the tag/genre, instead of it being separate
                        val tmp = clone()
                        tmp.selectFirst("p:contains(tag), p:contains(genre)")
                            ?.remove()
                        if (tmp.text().isNotBlank()) {
                            it.remove()
                        }
                    }

                    this@buildString.append(wholeText().trim())
                }

                if (!details.alternativeNames.isNullOrBlank()) {
                    if (isNotBlank()) {
                        append("\n\n")
                    }
                    append("Alternative Name: ", details.alternativeNames, "\n")
                }
            }
            genre = buildList {
                add(details.badge)
                add(details.year)
                if (details.adult) {
                    add("Adult")
                }
                addAll(tags.distinct())
            }.filterNotNull().joinToString()
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = DETAILS_REGEX.find(response.body.string())!!.groupValues[1]
            .unescape()
            .parseAs<ChapterList>()
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

    override fun pageListParse(response: Response): List<Page> {
        return response.asJsoup().select("img[alt^=chapter]").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.absUrl("src"))
        }
    }

    private fun String.unescape(): String {
        return UNESCAPE_REGEX.replace(this, "$1")
    }

    private inline fun <reified T> String.parseAs(): T {
        return json.decodeFromString(this)
    }

    private inline fun <reified T : Filter<*>> FilterList.get(): T? {
        return filterIsInstance<T>().firstOrNull()
    }

    override fun popularMangaRequest(page: Int) = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response) = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}

private val UNESCAPE_REGEX = """\\(.)""".toRegex()
private val DETAILS_REGEX = Regex("""info\\":(\{.*\}).*userIsFollowed""")
