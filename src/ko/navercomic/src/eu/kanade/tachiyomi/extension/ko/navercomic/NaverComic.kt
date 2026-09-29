package eu.kanade.tachiyomi.extension.ko.navercomic

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Response
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("yy.M.d")
private val seoulZone = ZoneId.of("Asia/Seoul")

@Source
abstract class NaverComic : KeiSource() {

    private val mType: String get() = when (name) {
        "Naver Webtoon Best Challenge" -> "bestChallenge"
        "Naver Webtoon Challenge" -> "challenge"
        else -> "webtoon"
    }

    private val isChallenge: Boolean get() = mType != "webtoon"

    internal val mobileUrl = "https://m.comic.naver.com"

    // 1.4 stored absolute m.comic.naver.com URLs for entries added from popular/latest
    private fun SManga.titleId() = url.substringAfter("titleId=").substringBefore("&")

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/$mType/list?titleId=${manga.titleId()}"

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage = if (isChallenge) {
        client.get("$baseUrl/api/$mType/list?order=VIEW&page=$page").let(::parseMangaList)
    } else {
        client.get("$mobileUrl/$mType/weekday?sort=ALL_READER").let(::parseMangaList)
    }

    private fun parseMangaList(response: Response): MangasPage {
        return if (isChallenge) {
            val apiResponse = response.parseAs<ApiMangaChallengeResponse>()
            val mangas = apiResponse.toSMangas(mType)

            val hasNextPage = apiResponse.pageInfo?.nextPage?.let { it != 0 } ?: mangas.isNotEmpty()
            MangasPage(mangas, hasNextPage)
        } else {
            val document = response.asJsoup()
            val mangas = document.select(".list_toon > [class='item ']").mapNotNull { element ->
                val url = element.selectFirst("a")?.attr("abs:href") ?: return@mapNotNull null
                val title = element.selectFirst("strong")?.text() ?: return@mapNotNull null

                SManga.create().apply {
                    setUrlWithoutDomain(url)
                    this.title = title
                    this.author = element.selectFirst("span.author")?.text()?.split(" / ")?.joinToString() ?: ""
                    this.thumbnail_url = element.selectFirst("img")?.attr("abs:src")
                }
            }
            MangasPage(mangas, false)
        }
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = if (isChallenge) {
        client.get("$baseUrl/api/$mType/list?order=UPDATE&page=$page").let(::parseMangaList)
    } else {
        client.get("$mobileUrl/$mType/weekday?sort=UPDATE").let(::parseMangaList)
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val result = client.get("$baseUrl/api/search/$mType?keyword=$query&page=$page")
            .parseAs<ApiMangaSearchResponse>()
        return MangasPage(result.toSMangas(mType), result.hasNextPage)
    }

    // ============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val titleId = manga.titleId()

        val details = if (fetchDetails) {
            async {
                client.get("$baseUrl/api/article/list/info?titleId=$titleId")
                    .parseAs<Manga>()
                    .toSManga(mType)
                    .apply { url = manga.url }
            }
        } else {
            null
        }

        val chapterList = if (fetchChapters) {
            async { fetchChapterList(titleId) }
        } else {
            null
        }

        SMangaUpdate(
            details?.await() ?: manga,
            chapterList?.await() ?: chapters,
        )
    }

    // ============================= Chapters ==============================

    private suspend fun fetchChapterList(titleId: String): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var page = 1

        while (true) {
            val result = client.get("$baseUrl/api/article/list?titleId=$titleId&page=$page")
                .parseAs<ApiMangaChapterListResponse>()

            chapters.addAll(
                result.articleList.map { chapter ->
                    chapter.toSChapter(mType, result.titleId, ::parseChapterDate)
                },
            )

            if (!result.hasNextPage) break

            page = result.pageInfo.nextPage
        }

        return chapters
    }

    private fun parseChapterDate(date: String): Long = if (date.contains(":")) {
        System.currentTimeMillis()
    } else {
        dateFormat.tryParseDate(date, seoulZone)
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()

        var urls = document.select(".wt_viewer img").map { it.attr("abs:src").ifEmpty { it.attr("src") } }
        if (urls.isEmpty()) {
            urls = document.select(".toon_view_lst img.toon_image").map {
                it.attr("abs:data-src").ifEmpty { it.attr("abs:src") }
            }
        }

        return urls.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }
}
