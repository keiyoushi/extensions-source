package eu.kanade.tachiyomi.extension.ko.navercomic

import android.annotation.SuppressLint
import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

abstract class NaverComicBase(protected val mType: String) : ParsedHttpSource() {
    override val lang: String = "ko"
    override val baseUrl: String = "https://comic.naver.com"
    internal val mobileUrl = "https://m.comic.naver.com"
    override val supportsLatest = true
    override val client: OkHttpClient = network.client
    internal val json: Json by injectLazy()

    private val mobileHeaders = super.headersBuilder()
        .add("Referer", mobileUrl)
        .build()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/api/search/$mType?keyword=$query&page=$page")

    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException("Not used")
    override fun searchMangaSelector() = throw UnsupportedOperationException("Not used")
    override fun searchMangaFromElement(element: Element) = throw UnsupportedOperationException("Not used")

    override fun searchMangaParse(response: Response): MangasPage {
        val result = json.decodeFromString<ApiMangaSearchResponse>(response.body.string())
        val mangas = result.searchList.map {
            SManga.create().apply {
                title = it.titleName
                description = it.synopsis
                thumbnail_url = it.thumbnailUrl
                url = "/$mType/list?titleId=${it.titleId}"
            }
        }

        return MangasPage(mangas, result.pageInfo.nextPage != 0)
    }

    override fun chapterListSelector() = throw UnsupportedOperationException("Not used")
    override fun chapterListRequest(manga: SManga) = chapterListRequest(manga.url, 1)
    private fun chapterListRequest(mangaUrl: String, page: Int): Request {
        val titleId = Uri.parse("$baseUrl$mangaUrl").getQueryParameter("titleId")
        return GET("$baseUrl/api/article/list?titleId=$titleId&page=$page")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        var result = json.decodeFromString<ApiMangaChapterListResponse>(response.body.string())
        val chapters = mutableListOf<SChapter>()
        chapters.addAll(result.articleList.map { createChapter(it, result.titleId) })

        while (result.pageInfo.nextPage != 0) {
            result = json.decodeFromString(client.newCall(chapterListRequest("/$mType/list?titleId=${result.titleId}", result.pageInfo.nextPage)).execute().body.string())
            chapters.addAll(result.articleList.map { createChapter(it, result.titleId) })
        }

        return chapters
    }

    private fun createChapter(chapter: MangaChapter, id: Int): SChapter {
        return SChapter.create().apply {
            url = "/$mType/detail?titleId=$id&no=${chapter.no}"
            name = chapter.subtitle
            chapter_number = chapter.no.toFloat()
            date_upload = parseChapterDate(chapter.serviceDateDescription)
        }
    }

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException("Not used")

    @SuppressLint("SimpleDateFormat")
    private fun parseChapterDate(date: String): Long {
        return if (date.contains(":")) {
            Calendar.getInstance().timeInMillis
        } else {
            return try {
                SimpleDateFormat("yy.MM.dd", Locale.KOREA).parse(date)?.time ?: 0L
            } catch (e: Exception) {
                e.printStackTrace()
                0
            }
        }
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val titleId = Uri.parse(manga.url).getQueryParameter("titleId")
        return client.newCall(GET("$baseUrl/api/article/list/info?titleId=$titleId")).asObservableSuccess().map { mangaDetailsParse(it) }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val manga = json.decodeFromString<Manga>(response.body.string())
        val authors = manga.communityArtists.joinToString { it.name }

        return SManga.create().apply {
            title = manga.titleName
            author = authors
            description = manga.synopsis
            thumbnail_url = manga.thumbnailUrl
            status = when {
                manga.rest -> SManga.ON_HIATUS
                manga.finished -> SManga.COMPLETED
                else -> SManga.ONGOING
            }
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        try {
            document.select(".wt_viewer img")
                .map {
                    it.attr("src")
                }
                .forEach {
                    pages.add(Page(pages.size, "", it))
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return pages
    }

    // We are able to get the image URL directly from the page list
    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()
}

abstract class NaverComicChallengeBase(mType: String) : NaverComicBase(mType) {
    override fun popularMangaSelector() = throw UnsupportedOperationException("Not used")
    override fun popularMangaNextPageSelector() = throw UnsupportedOperationException("Not used")
    override fun popularMangaFromElement(element: Element) = throw UnsupportedOperationException("Not used")
    override fun popularMangaParse(response: Response): MangasPage {
        val apiMangaResponse = json.decodeFromString<ApiMangaChallengeResponse>(response.body.string())
        val mangas = apiMangaResponse.list.map {
            SManga.create().apply {
                title = it.titleName
                thumbnail_url = it.thumbnailUrl
                url = "/$mType/list?titleId=${it.titleId}"
            }
        }

        var pageInfo = apiMangaResponse.pageInfo

        if (pageInfo == null) {
            val page = response.request.url.queryParameter("page")
            pageInfo = client.newCall(GET("$baseUrl/api/$mType/pageInfo?order=VIEW&page=$page")).execute().let { parsePageInfo(it) }
        }

        return MangasPage(mangas, pageInfo?.nextPage != 0)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)
    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    private fun parsePageInfo(response: Response): PageInfo? {
        return json.decodeFromString<ApiMangaChallengeResponse>(response.body.string()).pageInfo
    }
}

@Serializable
data class ApiMangaSearchResponse(
    val pageInfo: PageInfo,
    val searchList: List<Manga>,
)

@Serializable
data class ApiMangaChallengeResponse(
    val pageInfo: PageInfo?,
    val list: List<MangaChallenge>,
)

@Serializable
data class ApiMangaChapterListResponse(
    val pageInfo: PageInfo,
    val titleId: Int,
    val articleList: List<MangaChapter>,
)

@Serializable
data class PageInfo(
    val nextPage: Int,
)

@Serializable
data class MangaChapter(
    val serviceDateDescription: String,
    val subtitle: String,
    val no: Int,
)

@Serializable
data class Manga(
    val thumbnailUrl: String,
    val titleName: String,
    val titleId: Int,
    val finished: Boolean,
    val rest: Boolean,
    val communityArtists: List<Author>,
    val synopsis: String,
)

@Serializable
data class MangaChallenge(
    val thumbnailUrl: String,
    val titleName: String,
    val titleId: Int,
    val finish: Boolean,
    val author: String,
)

@Serializable
data class Author(
    val artistId: Int,
    val name: String,
)
