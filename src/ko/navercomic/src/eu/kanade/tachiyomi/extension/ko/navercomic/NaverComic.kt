package eu.kanade.tachiyomi.extension.ko.navercomic

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Source
abstract class NaverComic : HttpSource() {

    private val mType: String get() = when (name) {
        "Naver Webtoon Best Challenge" -> "bestChallenge"
        "Naver Webtoon Challenge" -> "challenge"
        else -> "webtoon"
    }

    private val isChallenge: Boolean get() = mType != "webtoon"

    internal val mobileUrl = "https://m.comic.naver.com"
    override val supportsLatest = true

    private val dateFormat by lazy {
        SimpleDateFormat("yy.MM.dd", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Seoul")
        }
    }

    private val challengeDateFormat by lazy {
        SimpleDateFormat("yyyy.MM.dd", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Seoul")
        }
    }

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = if (isChallenge) {
        GET("$baseUrl/api/$mType/list?order=VIEW&page=$page", headers)
    } else {
        GET("$mobileUrl/$mType/weekday?sort=ALL_READER", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        return if (isChallenge) {
            val apiResponse = response.parseAs<ApiMangaChallengeResponse>()
            val mangas = apiResponse.toSMangas(mType)

            // Assume there's a next page if we got results, avoiding extra synchronous network calls inside parse.
            val hasNextPage = apiResponse.pageInfo?.nextPage?.let { it != 0 } ?: mangas.isNotEmpty()
            MangasPage(mangas, hasNextPage)
        } else {
            val document = response.asJsoup()
            val mangas = document.select(".list_toon > [class='item ']").mapNotNull { element ->
                val url = element.selectFirst("a")?.attr("abs:href") ?: return@mapNotNull null
                val title = element.selectFirst("strong")?.text() ?: return@mapNotNull null

                SManga.create().apply {
                    this.url = url
                    this.title = title
                    this.author = element.selectFirst("span.author")?.text()?.split(" / ")?.joinToString() ?: ""
                    this.thumbnail_url = element.selectFirst("img")?.attr("abs:src")
                }
            }
            MangasPage(mangas, false)
        }
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = if (isChallenge) {
        GET("$baseUrl/api/$mType/list?order=UPDATE&page=$page", headers)
    } else {
        GET("$mobileUrl/$mType/weekday?sort=UPDATE", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/api/search/$mType?keyword=$query&page=$page", headers)

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<ApiMangaSearchResponse>()
        return MangasPage(result.toSMangas(mType), result.hasNextPage)
    }

    // ============================== Details ==============================

    override fun mangaDetailsRequest(manga: SManga): Request {
        val titleId = (baseUrl + manga.url).toHttpUrl().queryParameter("titleId")
        return GET("$baseUrl/api/article/list/info?titleId=$titleId", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<Manga>().toSManga(mType)

    // ============================= Chapters ==============================

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
                    chapter.toSChapter(mType, result.titleId, ::parseChapterDate)
                },
            )

            if (!result.hasNextPage) break

            val nextRequest = chapterListRequest("/$mType/list?titleId=${result.titleId}", result.pageInfo.nextPage)
            result = client.newCall(nextRequest).execute().parseAs<ApiMangaChapterListResponse>()
        }

        return chapters
    }

    private fun parseChapterDate(date: String): Long = if (date.contains(":")) {
        System.currentTimeMillis()
    } else {
        val formatter = if (name == "Naver Webtoon Challenge") challengeDateFormat else dateFormat
        formatter.tryParse(date)
    }

    // =============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        var urls = document.select(".wt_viewer img").map { it.attr("abs:src").ifEmpty { it.attr("src") } }
        if (urls.isEmpty()) {
            urls = document.select(".toon_view_lst img.toon_image").map {
                it.attr("abs:data-src").ifEmpty { it.attr("abs:src") }
            }
        }

        return urls.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList() = FilterList()
}
