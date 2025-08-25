package eu.kanade.tachiyomi.extension.all.mangapluscreators

import eu.kanade.tachiyomi.network.GET
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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy

class MangaPlusCreators(override val lang: String) : HttpSource() {

    override val name = "MANGA Plus Creators by SHUEISHA"

    override val baseUrl = BASE_URL

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
//        .add("Origin", baseUrl.substringBeforeLast("/"))
        .add("Referer", baseUrl)
        .add("User-Agent", USER_AGENT)

    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int): Request {
        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/titles/popular/?p=m")
            .build()

        val popularUrl = "$baseUrl/titles/popular/?p=m&l=$lang".toHttpUrl().toString()

        return GET(popularUrl, newHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.asJsoup()

        val mangas = result.select("div.item-recent").map { element ->
            popularElementToSManga(element)
        }

        return MangasPage(mangas, false)
    }

    fun popularElementToSManga(element: org.jsoup.nodes.Element): SManga {
        val titleThumbnailUrl = element.select(".image-area img").attr("src")
        val titleContentId = titleThumbnailUrl.toHttpUrl().pathSegments[2]
        return SManga.create().apply {
            title = element.select(".title-area .title").text().toString()
            thumbnail_url = titleThumbnailUrl
            setUrlWithoutDomain("/titles/$titleContentId")
        }
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/titles/recent/?t=episode")
            .build()

        val apiUrl = "$API_URL/titles/recent/".toHttpUrl().newBuilder()
            .addQueryParameter("page", testLastPage)
            .addQueryParameter("l", lang)
            .addQueryParameter("t", "episode")
            .toString()

        return GET(apiUrl, newHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = json.decodeFromString<MpcResponse>(response.body.string())

        checkNotNull(result.titles) { EMPTY_RESPONSE_ERROR }

        val titles = result.titles.titleList.orEmpty().map(MpcTitle::toSManga)

        return MangasPage(titles, result.status != "error")
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val searchUrl = "$baseUrl/keywords".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("s", "date")
            .addQueryParameter("lang", lang)
            .toString()

        return GET(searchUrl, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.asJsoup()
        val bookBox = result.selectFirst(".book-box")!!

        return SManga.create().apply {
            title = bookBox.selectFirst("div.title")!!.text()
            author = bookBox.selectFirst("div.mod-btn-profile div.name")!!.text()
            description = bookBox.select("div.summary p")
                .joinToString("\n\n") { it.text() }
            status = when (bookBox.selectFirst("div.book-submit-type")!!.text()) {
                "Series" -> SManga.ONGOING
                "One-shot" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            genre = bookBox.select("div.genre-area div.tag-genre")
                .joinToString(", ") { it.text() }
            thumbnail_url = bookBox.selectFirst("div.cover img")!!.attr("data-src")
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET("${manga.url}/?page=1")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.asMpcResponse()

        checkNotNull(result.episodes) { EMPTY_RESPONSE_ERROR }

        return result.episodes.episodeList.orEmpty()
            .sortedByDescending(MpcEpisode::numbering)
            .map(MpcEpisode::toSChapter)
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(chapter.url)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.asMpcResponse()

        checkNotNull(result.pageList) { EMPTY_RESPONSE_ERROR }

        val referer = response.request.header("Referer")!!

        return result.pageList.mapIndexed { i, page ->
            Page(i, referer, page.publicBgImage)
        }
    }

    override fun fetchImageUrl(page: Page): Observable<String> = Observable.just(page.imageUrl!!)

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .removeAll("Origin")
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    companion object {
        private const val BASE_URL = "https://mangaplus-creators.jp"
        private const val API_URL = "$BASE_URL/api"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.0.0 Safari/537.36"

        private const val POPULAR_PAGE_SIZE = "30"
        private const val CHAPTER_PAGE_SIZE = "200"

        private const val EMPTY_RESPONSE_ERROR = "Empty response from the API. Try again later."
    }
}
