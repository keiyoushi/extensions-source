package eu.kanade.tachiyomi.extension.pt.bruttal

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.TimeUnit

class Bruttal : HttpSource() {

    override val name = "Bruttal"

    override val baseUrl = BRUTTAL_URL

    override val lang = "pt-BR"

    override val supportsLatest = false

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", "$baseUrl/bruttal/")
        .add("User-Agent", USER_AGENT)

    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int): Request {
        val newHeaders = headersBuilder()
            .add("Accept", "application/json, text/plain, */*")
            .build()

        return GET("$baseUrl/data/home.json", newHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val titles = response.parseAs<BruttalHomeDto>().list
            .map(BruttalComicBookDto::toSManga)

        return MangasPage(titles, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val newHeaders = headersBuilder()
            .add("Accept", "application/json, text/plain, */*")
            .build()

        val jsonUrl = "$baseUrl/data/home.json".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .toString()

        return GET(jsonUrl, newHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val query = response.request.url.queryParameter("q").orEmpty()

        var titles = response.parseAs<BruttalHomeDto>().list
            .map(BruttalComicBookDto::toSManga)

        if (query.isNotEmpty()) {
            titles = titles.filter { it.title.contains(query, ignoreCase = true) }
        }

        return MangasPage(titles, false)
    }

    // Workaround to allow "Open in browser" use the real URL.
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsApiRequest(manga))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response).apply { initialized = true }
            }
    }

    private fun mangaDetailsApiRequest(manga: SManga): Request {
        val newHeaders = headersBuilder()
            .add("Accept", "application/json, text/plain, */*")
            .set("Referer", baseUrl + manga.url)
            .build()

        return GET("$baseUrl/data/comicbooks.json", newHeaders)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val comicBookUrl = response.request.header("Referer")!!
            .substringAfter("/bruttal")

        return response.parseAs<List<BruttalComicBookDto>>()
            .first { it.url == comicBookUrl }
            .toSManga()
    }

    // Chapters are available in the same url of the manga details.
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsApiRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val comicBooks = response.parseAs<List<BruttalComicBookDto>>()

        val comicBookUrl = response.request.header("Referer")!!
            .substringAfter("/bruttal")
        val currentComicBook = comicBooks.first { it.url == comicBookUrl }

        return currentComicBook.seasons
            .flatMap(BruttalSeasonDto::chapters)
            .map(BruttalChapterDto::toSChapter)
            .reversed()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val newHeaders = headersBuilder()
            .add("Accept", "application/json, text/plain, */*")
            .set("Referer", baseUrl + chapter.url)
            .build()

        return GET("$baseUrl/data/comicbooks.json", newHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val comicBooks = response.parseAs<List<BruttalComicBookDto>>()

        val chapterUrl = response.request.header("Referer")!!
        val comicBookSlug = chapterUrl
            .substringAfter("bruttal/")
            .substringBefore("/")
        val seasonNumber = chapterUrl
            .substringAfter("temporada-")
            .substringBefore("/")
        val chapterNumber = chapterUrl.substringAfter("capitulo-")

        val currentComicBook = comicBooks.first { it.url == "/$comicBookSlug" }
        val currentSeason = currentComicBook.seasons.first {
            it.alias.substringAfter("-") == seasonNumber
        }
        val currentChapter = currentSeason.chapters.first {
            it.alias.substringAfter("-") == chapterNumber
        }

        return currentChapter.images
            .mapIndexed { i, imageDto ->
                val imageUrl = "$baseUrl/${imageDto.image.removePrefix("./")}"
                Page(i, chapterUrl, imageUrl)
            }
    }

    override fun fetchImageUrl(page: Page): Observable<String> = Observable.just(page.imageUrl!!)

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .add("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    override fun latestUpdatesRequest(page: Int): Request =
        throw UnsupportedOperationException("Not used")

    override fun latestUpdatesParse(response: Response): MangasPage =
        throw UnsupportedOperationException("Not used")

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromString(body.string())
    }

    companion object {
        const val BRUTTAL_URL = "https://originals.omelete.com.br/bruttal"

        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/106.0.0.0 Safari/537.36"
    }
}
