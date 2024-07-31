package eu.kanade.tachiyomi.extension.en.reaperscans

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
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class ReaperScans : HttpSource() {

    override val name = "Reaper Scans"

    override val baseUrl = "https://reaperscans.com"

    override val lang = "en"

    override val id = 5177220001642863679

    override val supportsLatest = true

    private val apiHost = "api.reaperscans.com"

    private val defaultQueryApiUrl get() = HttpUrl.Builder()
        .scheme("https")
        .host(apiHost)
        .addPathSegment("query")
        .addQueryParameter("perPage", "999")
        .addQueryParameter("series_type", "Comic")
        .addQueryParameter("adult", "true") // ???

    override val versionId: Int = 2

    private val json: Json by injectLazy()

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val headers = request.headers.newBuilder()
                .removeAll("X-Requested-With")
                .build()
            chain.proceed(request.newBuilder().headers(headers).build())
        }
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .set("X-Requested-With", randomString((1..20).random())) // For WebView, removed in interceptor

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET(
        defaultQueryApiUrl
            .addQueryParameter("page", page.toString())
            .addQueryParameter("query_string", "")
            .addQueryParameter("order", "desc")
            .addQueryParameter("orderBy", "total_views")
            .build(),
        headers,
    )

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseJson<SeriesQueryDto>()

        val mangas = data.data.map {
            SManga.create().apply {
                title = it.title
                // Don't know what "4SRBHm" is for but it seems constant across all series
                thumbnail_url = "https://media.reaperscans.com/file/4SRBHm/${it.thumbnail}"
                url = "/series/${it.slug}#${it.id}"
                status = when (it.status) {
                    "Hiatus" -> SManga.ON_HIATUS
                    "Completed" -> SManga.COMPLETED
                    "Ongoing" -> SManga.ONGOING
                    "Dropped" -> SManga.CANCELLED
                    else -> SManga.UNKNOWN
                }
                description = it.description
            }
        }
        return MangasPage(mangas, data.meta.lastPage != data.meta.currentPage)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = GET(
        defaultQueryApiUrl
            .addQueryParameter("page", page.toString())
            .addQueryParameter("query_string", "")
            .addQueryParameter("order", "desc")
            .addQueryParameter("orderBy", "updated_at")
            .build(),
        headers,
    )

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterString = filters.filterIsInstance<TagFilter>().filter { it.state }.map { it.id }.joinToString(",")
        android.util.Log.i("ReaperScans", "filterString: $filterString")
        val url = defaultQueryApiUrl
            .addQueryParameter("page", page.toString())
            .addQueryParameter("query_string", query)
            .addQueryParameter("order", filters.filterIsInstance<OrderBySortFilter>().first().selected)
            .addQueryParameter("orderBy", filters.filterIsInstance<OrderFilter>().first().selected)
            .addQueryParameter("status", filters.filterIsInstance<StatusFilter>().first().selected)
            .addEncodedQueryParameter("tags_ids", "[$filterString]")
        return GET(
            url.build(),
            headers,
        )
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(PREFIX_ID_SEARCH)) {
            val realUrl = "/series/" + query.removePrefix(PREFIX_ID_SEARCH)
            val manga = SManga.create().apply {
                url = realUrl
            }
            return fetchMangaDetails(manga).map {
                MangasPage(listOf(it.apply { url = realUrl }), false)
            }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    // Details

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(
            HttpUrl.Builder()
                .scheme("https")
                .host(apiHost)
                .addPathSegments(manga.url.substringBefore('#').trimStart('/'))
                .build(),
            headers,
        )
    }

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl${manga.url}"
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.parseJson<SeriesDto>()
        return SManga.create().apply {
            title = data.title
            thumbnail_url = "https://media.reaperscans.com/file/4SRBHm/${data.thumbnail}"
            url = "/series/${data.slug}#${data.id}"
            status = when (data.status) {
                "Hiatus" -> SManga.ON_HIATUS
                "Completed" -> SManga.COMPLETED
                "Ongoing" -> SManga.ONGOING
                "Dropped" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
            description = data.description
            author = data.author
            genre = data.tags.joinToString(", ") { it.name }
        }
    }

    // Chapters

    override fun chapterListRequest(manga: SManga): Request {
        val seriesId = manga.url.substringAfter('#')
        return GET(
            HttpUrl.Builder()
                .scheme("https")
                .host(apiHost)
                .addPathSegments("chapter/query")
                .addQueryParameter("page", "1")
                .addQueryParameter("perPage", "9999")
                .addQueryParameter("series_id", seriesId)
                .build(),
            headers,
        )
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseJson<ChapterQueryDto>()

        return data.data.map {
            SChapter.create().apply {
                name = if (it.title != null) {
                    "${it.name} - ${it.title}"
                } else {
                    it.name
                }
                url = "/series/${it.series.slug}/${it.slug}"
                date_upload = DATE_FORMAT.parse(it.created)!!.time
            }
        } // if there are more then 999 chapters, we have bigger problems
    }

    // Page
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.container > div.items-center.justify-center > img[loading=lazy]").mapIndexed { index, element ->
            Page(index, imageUrl = element.imgAttr())
        }
    }

    // Filters
    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>()
        filters += OrderFilter()
        filters += OrderBySortFilter()
        filters += StatusFilter()
        filters += Filter.Header("Tags")
        filters += TagFilter("Action", 1)
        filters += TagFilter("Fantasy", 2)
        filters += TagFilter("Romance", 3)
        filters += TagFilter("Martial Arts", 4)
        filters += TagFilter("Gaming", 5)
        filters += TagFilter("Comedy", 6)
        filters += TagFilter("Horror", 7)
        filters += TagFilter("Drama", 8)
        return FilterList(filters)
    }

    // Helpers
    private inline fun <reified T> Response.parseJson(): T = use {
        it.body.string().parseJson()
    }

    private inline fun <reified T> String.parseJson(): T = json.decodeFromString(this)

    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        else -> attr("abs:src")
    }

    private fun randomString(length: Int): String {
        val charPool = ('a'..'z') + ('A'..'Z')
        return List(length) { charPool.random() }.joinToString("")
    }

    // Unused
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
    companion object {
        const val PREFIX_ID_SEARCH = "id:"

        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    }
}
