package eu.kanade.tachiyomi.extension.ko.navercomic

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

abstract class NaverComicBase(protected val mType: String) : HttpSource() {
    override val lang: String = "ko"
    override val baseUrl: String = "https://comic.naver.com"
    internal val mobileUrl = "https://m.comic.naver.com"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    protected open val dateFormat = SimpleDateFormat("yy.MM.dd", Locale.KOREA)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/api/search/$mType?keyword=$query&page=$page", headers)

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<ApiMangaSearchResponse>()
        return MangasPage(result.toSMangas(mType), result.hasNextPage)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val titleId = (baseUrl + manga.url).toHttpUrl().queryParameter("titleId")
        return GET("$baseUrl/api/article/list/info?titleId=$titleId", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<Manga>().toSManga(mType)

    override fun chapterListRequest(manga: SManga): Request = chapterListRequest(manga.url, 1)

    private fun chapterListRequest(mangaUrl: String, page: Int): Request {
        val titleId = (baseUrl + mangaUrl).toHttpUrl().queryParameter("titleId")
        return GET("$baseUrl/api/article/list?titleId=$titleId&page=$page", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        var result = response.parseAs<ApiMangaChapterListResponse>()
        val chapters = mutableListOf<SChapter>()

        while (true) {
            chapters.addAll(
                result.articleList.map { chapter ->
                    chapter.toSChapter(mType, result.titleId).apply {
                        date_upload = parseChapterDate(chapter.serviceDateDescription)
                    }
                },
            )

            if (!result.hasNextPage) break

            val nextRequest = chapterListRequest("/$mType/list?titleId=${result.titleId}", result.pageInfo.nextPage)
            result = client.newCall(nextRequest).execute().parseAs<ApiMangaChapterListResponse>()
        }

        return chapters
    }

    protected fun parseChapterDate(date: String): Long = if (date.contains(":")) {
        Calendar.getInstance().timeInMillis
    } else {
        dateFormat.tryParse(date)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        var urls = document.select(".wt_viewer img").map { it.attr("abs:src").ifEmpty { it.attr("src") } }
        if (urls.isEmpty()) {
            urls = document.select(".toon_view_lst img").map { it.attr("abs:data-src").ifEmpty { it.attr("data-src") } }
        }

        return urls.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList()
}

abstract class NaverComicChallengeBase(mType: String) : NaverComicBase(mType) {

    override fun popularMangaParse(response: Response): MangasPage {
        val apiMangaResponse = response.parseAs<ApiMangaChallengeResponse>()
        val mangas = apiMangaResponse.toSMangas(mType)

        var pageInfo = apiMangaResponse.pageInfo

        if (pageInfo == null) {
            val page = response.request.url.queryParameter("page")
            val pageInfoResponse = client.newCall(GET("$baseUrl/api/$mType/pageInfo?order=VIEW&page=$page", headers)).execute()
            pageInfo = pageInfoResponse.parseAs<ApiMangaChallengeResponse>().pageInfo
        }

        return MangasPage(mangas, pageInfo?.nextPage ?: 0 != 0)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)
}
