package eu.kanade.tachiyomi.multisrc.pizzareader

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

abstract class PizzaReader(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    private val apiPath: String = "/api",
    private val dateParser: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.ITALY),
) : HttpSource() {

    override val supportsLatest = true

    open val apiUrl by lazy { "$baseUrl$apiPath" }

    protected open val json: Json by injectLazy()

    override fun headersBuilder() = Headers.Builder().apply {
        add("Referer", baseUrl)
    }

    override fun popularMangaRequest(page: Int) =
        GET("$apiUrl/comics", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val result = json.decodeFromString<PizzaResultsDto>(response.body.string())

        val comicList = result.comics
            .map(::popularMangaFromObject)

        return MangasPage(comicList, hasNextPage = false)
    }

    protected open fun popularMangaFromObject(comic: PizzaComicDto): SManga = SManga.create().apply {
        title = comic.title
        thumbnail_url = comic.thumbnail
        url = comic.url
    }

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = json.decodeFromString<PizzaResultsDto>(response.body.string())

        val comicList = result.comics
            .filter { comic -> comic.lastChapter != null }
            .sortedByDescending { comic -> comic.lastChapter!!.publishedOn }
            .map(::popularMangaFromObject)
            .take(10)

        return MangasPage(comicList, hasNextPage = false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val searchUrl = "$apiUrl/search/".toHttpUrl().newBuilder()
            .addPathSegment(query)
            .toString()

        return GET(searchUrl, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // Workaround to allow "Open in browser" to use the real URL
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        client.newCall(chapterListRequest(manga)).asObservableSuccess()
            .map { mangaDetailsParse(it).apply { initialized = true } }

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val result = json.decodeFromString<PizzaResultDto>(response.body.string())
        val comic = result.comic!!

        title = comic.title
        author = comic.author
        artist = comic.artist
        description = comic.description
        genre = comic.genres.joinToString(", ") { it.name }
        status = comic.status.toStatus()
        thumbnail_url = comic.thumbnail
    }

    override fun chapterListRequest(manga: SManga) = GET(apiUrl + manga.url, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = json.decodeFromString<PizzaResultDto>(response.body.string())
        val comic = result.comic!!

        return comic.chapters
            .map(::chapterFromObject)
    }

    protected open fun chapterFromObject(chapter: PizzaChapterDto): SChapter = SChapter.create().apply {
        name = chapter.fullTitle
        chapter_number = (chapter.chapter ?: -1).toFloat() +
            ("0." + (chapter.subchapter?.toString() ?: "0")).toFloat()
        date_upload = chapter.publishedOn.toDate()
        scanlator = chapter.teams.filterNotNull()
            .joinToString(" & ") { it.name }
        url = chapter.url
    }

    override fun pageListRequest(chapter: SChapter) = GET(apiUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val result = json.decodeFromString<PizzaReaderDto>(response.body.string())

        return result.chapter!!.pages.mapIndexed { i, page -> Page(i, "", page) }
    }

    override fun imageUrlParse(response: Response): String = ""

    protected open fun String.toDate(): Long {
        return runCatching { dateParser.parse(this)?.time }
            .getOrNull() ?: 0L
    }

    protected open fun String.toStatus(): Int = when (substring(0, 7)) {
        "In cors" -> SManga.ONGOING
        "On goin" -> SManga.ONGOING
        "Complet" -> SManga.COMPLETED
        "Conclus" -> SManga.COMPLETED
        "Conclud" -> SManga.COMPLETED
        "Licenzi" -> SManga.LICENSED
        "License" -> SManga.LICENSED
        else -> SManga.UNKNOWN
    }
}
