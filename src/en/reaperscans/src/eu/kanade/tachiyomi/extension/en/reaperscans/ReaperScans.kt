package eu.kanade.tachiyomi.extension.en.reaperscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class ReaperScans : ParsedHttpSource() {

    override val name = "Reaper Scans"

    override val baseUrl = "https://reaperscans.com"

    override val lang = "en"

    override val id = 5177220001642863679

    override val supportsLatest = true

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
    override fun popularMangaRequest(page: Int): Request = GET("https://api.reaperscans.com/query?page=$page&perPage=20&series_type=Comic&query_string=&order=desc&orderBy=total_views&adult=true", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseJson<SeriesQueryDto>()

        val mangas = data.data.map {
            SManga.create().apply {
                title = it.title
                // Don't know what "4SRBHm" is for but it seems constant across all series
                thumbnail_url = "https://media.reaperscans.com/file/4SRBHm/${it.thumbnail}"
                url = "/series/${it.slug}"
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
    override fun latestUpdatesRequest(page: Int): Request = GET("https://api.reaperscans.com/query?page=$page&perPage=20&series_type=Comic&query_string=&order=desc&orderBy=updated_at&adult=true", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("https://api.reaperscans.com/query?page=$page&perPage=20&series_type=Comic&query_string=${query.replace(' ', '+')}&order=desc&orderBy=total_views&adult=true", headers)

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
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            thumbnail_url = document.select("div.bg-background > img").first()!!.imgAttr()
            title = document.select("h1").first()!!.text()

            status = when (document.select("div.flex-row > span.rounded").first()!!.text()) {
                "Hiatus" -> SManga.ON_HIATUS
                "Completed" -> SManga.COMPLETED
                "Ongoing" -> SManga.ONGOING
                "Dropped" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }

            genre = document.select("div.flex-row > span.rounded").drop(1) // drop status
                .joinToString(", ") { it.text() }
            author = document.select(".space-y-2 > div:nth-child(2) > span:nth-child(2)").first()!!.text()
            description = document.select("div.text-muted-foreground > div").first()!!.text()
        }
    }

    // Chapters

    override fun chapterListRequest(manga: SManga): Request {
        // this is extremely hacky.
        val response = client.newCall(searchMangaRequest(1, manga.title, FilterList())).execute()
        val data = response.parseJson<SeriesQueryDto>()
        val seriesId = data.data.first().id
        return GET("https://api.reaperscans.com/chapter/query?page=1&perPage=30&series_id=$seriesId", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        var data = response.parseJson<ChapterQueryDto>()
        if (data.data.isEmpty()) {
            return emptyList()
        }
        val seriesId = data.data.first().series.id
        val chapters = mutableListOf<SChapter>()
        do {
            chapters.addAll(
                data.data.map {
                    SChapter.create().apply {
                        name = if (it.title != null) {
                            "${it.name} - ${it.title}"
                        } else {
                            it.name
                        }
                        url = "/series/${it.series.slug}/${it.slug}"
                        date_upload = DATE_FORMAT.parse(it.created)!!.time
                    }
                },
            )
            if (data.meta.currentPage != data.meta.lastPage) {
                data = client.newCall(GET("https://api.reaperscans.com/chapter/query?page=${data.meta.currentPage + 1}&perPage=30&series_id=$seriesId", headers)).execute().parseJson()
            } else {
                break
            }
        } while (data.meta.currentPage <= data.meta.lastPage)
        return chapters
    }

    // Page
    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.items-center.justify-center > img[data-src]").mapIndexed { index, element ->
            val imageUrl = element.attr("src").ifEmpty {
                element.attr("data-src")
            }
            Page(index, imageUrl = imageUrl)
        }
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
    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException()

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector(): String = throw UnsupportedOperationException()

    override fun latestUpdatesSelector(): String = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun popularMangaFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun searchMangaSelector(): String = throw UnsupportedOperationException()

    override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException()

    override fun popularMangaSelector(): String = throw UnsupportedOperationException()

    override fun popularMangaNextPageSelector(): String = throw UnsupportedOperationException()

    override fun chapterListSelector() = throw UnsupportedOperationException()

    companion object {
        const val PREFIX_ID_SEARCH = "id:"

        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    }
}
