package eu.kanade.tachiyomi.extension.fr.manganova

import android.webkit.CookieManager
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.net.URI

class MangaNova : HttpSource() {

    override val name = "MangaNova"
    override val baseUrl = "https://www.manga-nova.com"
    val api = "https://api.manga-nova.com"
    override val lang = "fr"
    override val supportsLatest = true

    private val webViewCookieManager: CookieManager by lazy { CookieManager.getInstance() }

    // Default static token, shouldn't change
    private val defaultToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJtZW1icmVfaWQiOjAsIm1lbWJyZV91c2VybmFtZSI6bnVsbCwiaWF0IjoxNzA1NTc5MDQ1fQ.51qivLd2l3OKbDaYYzlntZJNnreRSBWO7p5Nsa2mAsA"

    override fun headersBuilder(): Headers.Builder {
        val cookies = webViewCookieManager.getCookie(baseUrl)
        var token = defaultToken
        if (cookies != null && cookies.isNotEmpty()) {
            val cookieHeaders = cookies.split("; ").toList()
            val tokenCookie = cookieHeaders.firstOrNull { it.startsWith("token=") }
            if (tokenCookie != null) {
                token = tokenCookie.replace("token=", "")
            }
        }
        return super.headersBuilder()
            .add("Authorization", "Bearer $token")
    }

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET("$api/catalogue/", headers)

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val catalogue = response.parseAs<Catalogue>()
        val mangaList = mutableListOf<SManga>()

        for (serie in catalogue.newSeries) {
            mangaList.add(serie.toDetailedSManga())
        }
        return MangasPage(mangaList, false)
    }

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotBlank()) {
            "$api/catalogue/#$query"
        } else {
            "$api/catalogue/"
        }
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val catalogue = response.parseAs<Catalogue>()
        val mangaList = mutableListOf<SManga>()

        val fragment = response.request.url.fragment
        val searchQuery = fragment ?: ""

        if (searchQuery.startsWith("SLUG:")) {
            val serie = catalogue.series.find { it.slug == searchQuery.removePrefix("SLUG:") }
            if (serie != null) {
                mangaList.add(serie.toDetailedSManga())
            }
            return MangasPage(mangaList, false)
        }

        for (serie in catalogue.series) {
            if (searchQuery.isBlank() ||
                serie.title.contains(searchQuery, ignoreCase = true) ||
                serie.titleJap.contains(searchQuery, ignoreCase = true)
            ) {
                mangaList.add(serie.toDetailedSManga())
            }
        }

        return MangasPage(mangaList, false)
    }

    // Details
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val splitedPath = URI(manga.url).path.split("/")
        val slug = splitedPath[2]
        return client.newCall(GET("$api/catalogue/", headers))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response, slug)
            }
    }

    private fun mangaDetailsParse(response: Response, slug: String = ""): SManga {
        val catalogue = response.parseAs<Catalogue>()
        val series = catalogue.series
        val serie = series.find { it.slug == slug }
        if (serie == null) {
            throw UnsupportedOperationException("Bad SLUG")
        }
        return serie.toDetailedSManga()
    }

    // Pages
    override fun pageListRequest(chapter: SChapter): Request {
        val splitedPath = URI(chapter.url).path.split("/")
        val slug = splitedPath[2]
        val chapterNumber = splitedPath[4]
        return GET("$api/mangas/$slug/chapitres/$chapterNumber", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val images = response.parseAs<ChapterDetails>().images
        return images.mapIndexed { index, pageData ->
            Page(pageData.pageNumber, imageUrl = pageData.image)
        }
    }

    // Chapters
    override fun chapterListRequest(manga: SManga): Request {
        val splitedPath = URI(manga.url).path.split("/")
        val slug = splitedPath[2]
        return GET("$api/mangas/$slug", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val serie = response.parseAs<DetailedSerieContainer>().serie
        val categories = serie.chapitres
        val chapterList = mutableListOf<SChapter>()

        val currentEpoch = System.currentTimeMillis()
        for (category in categories) {
            for (chapter in category.chapitres) {
                if (chapter.amount != 0) continue

                val chapter = SChapter.create().apply {
                    name = category.title + " - " + chapter.title + " - " + chapter.subTitle
                    setUrlWithoutDomain("$baseUrl/lecture-en-ligne/${serie.slug}/chapitre/${chapter.number}")
                    chapter_number = chapter.number
                    date_upload = currentEpoch + (chapter.availableTime * 1000L)
                }
                chapterList.add(chapter)
            }
        }

        return chapterList.sortedByDescending { it.chapter_number }
    }

    // Unsupported stuff
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()
}
