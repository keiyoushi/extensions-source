package eu.kanade.tachiyomi.extension.zh.dmzj

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Dmzj source
 */

class Dmzj : ConfigurableSource, HttpSource() {
    override val lang = "zh"
    override val supportsLatest = true
    override val name = "动漫之家"
    override val baseUrl = "https://m.dmzj.com"

    private val preferences: SharedPreferences =
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(ImageUrlInterceptor)
        .addInterceptor(CommentsInterceptor)
        .rateLimit(4)
        .build()

    // API v4 randomly fails
    private val retryClient = network.client.newBuilder()
        .addInterceptor(RetryInterceptor)
        .rateLimit(2)
        .build()

    private fun fetchIdBySlug(slug: String): String {
        val request = GET("https://manhua.dmzj.com/$slug/", headers)
        val html = client.newCall(request).execute().body.string()
        val start = "g_comic_id = \""
        val startIndex = html.indexOf(start) + start.length
        val endIndex = html.indexOf('"', startIndex)
        return html.substring(startIndex, endIndex)
    }

    private fun fetchMangaInfoV4(id: String): ApiV4.MangaDto? {
        val response = retryClient.newCall(GET(ApiV4.mangaInfoUrl(id), headers)).execute()
        return ApiV4.parseMangaInfo(response)
    }

    override fun popularMangaRequest(page: Int) = GET(ApiV3.popularMangaUrl(page), headers)

    override fun popularMangaParse(response: Response) = ApiV3.parsePage(response)

    override fun latestUpdatesRequest(page: Int) = GET(ApiV3.latestUpdatesUrl(page), headers)

    override fun latestUpdatesParse(response: Response) = ApiV3.parsePage(response)

    private fun searchMangaById(id: String): MangasPage {
        val idNumber = if (id.all { it.isDigit() }) {
            id
        } else {
            // Chinese Pinyin ID
            fetchIdBySlug(id)
        }

        val sManga = fetchMangaDetails(idNumber)

        return MangasPage(listOf(sManga), false)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.isEmpty()) {
            val ranking = filters.filterIsInstance<RankingGroup>().firstOrNull()
            if (ranking != null && ranking.isEnabled) {
                val call = retryClient.newCall(GET(ApiV4.rankingUrl(page, ranking), headers))
                return Observable.fromCallable {
                    val result = ApiV4.parseRanking(call.execute())
                    // result has no manga ID if filtered by certain genres; this can be slow
                    for (manga in result.mangas) if (manga.url.startsWith(PREFIX_ID_SEARCH)) {
                        manga.url = getMangaUrl(fetchIdBySlug(manga.url.removePrefix(PREFIX_ID_SEARCH)))
                    }
                    result
                }
            }
            val call = client.newCall(GET(ApiV3.pageUrl(page, filters), headers))
            Observable.fromCallable { ApiV3.parsePage(call.execute()) }
        } else if (query.startsWith(PREFIX_ID_SEARCH)) {
            // ID may be numbers or Chinese pinyin
            val id = query.removePrefix(PREFIX_ID_SEARCH).removeSuffix(".html")
            Observable.fromCallable { searchMangaById(id) }
        } else {
            val request = GET(ApiSearch.textSearchUrl(query), headers)
            Observable.fromCallable {
                // this API fails randomly, and might return empty list
                repeat(5) {
                    val result = ApiSearch.parsePage(client.newCall(request).execute())
                    if (result.mangas.isNotEmpty()) return@fromCallable result
                }
                throw Exception("搜索出错或无结果")
            }
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        throw UnsupportedOperationException()
    }

    override fun searchMangaParse(response: Response): MangasPage {
        throw UnsupportedOperationException()
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val id = manga.url.extractMangaId()
        return Observable.fromCallable { fetchMangaDetails(id) }
    }

    private fun fetchMangaDetails(id: String): SManga {
        fetchMangaInfoV4(id)?.run { return toSManga() }
        val response = client.newCall(GET(ApiV3.mangaInfoUrlV1(id), headers)).execute()
        return ApiV3.parseMangaDetailsV1(response)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        throw UnsupportedOperationException()
    }

    override fun getMangaUrl(manga: SManga): String {
        val cid = manga.url.extractMangaId()
        return "$baseUrl/info/$cid.html"
    }

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        throw UnsupportedOperationException()
    }

    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException()

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.fromCallable {
            val id = manga.url.extractMangaId()
            val result = fetchMangaInfoV4(id)
            if (result != null && !result.isLicensed) {
                return@fromCallable result.parseChapterList()
            }
            val response = client.newCall(GET(ApiV3.mangaInfoUrlV1(id), headers)).execute()
            ApiV3.parseChapterListV1(response)
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        throw UnsupportedOperationException()
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/view/${chapter.url}.html"

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val path = chapter.url
        return Observable.fromCallable {
            val response = retryClient.newCall(GET(ApiV4.chapterImagesUrl(path), headers)).execute()
            val result = try {
                ApiV4.parseChapterImages(response, preferences.imageQuality == LOW_RES)
            } catch (_: Throwable) {
                client.newCall(GET(ApiV3.chapterImagesUrlV1(path), headers)).execute()
                    .let(ApiV3::parseChapterImagesV1)
            }
            if (preferences.showChapterComments) {
                result.add(Page(result.size, COMMENTS_FLAG, ApiV3.chapterCommentsUrl(path)))
            }
            result
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        throw UnsupportedOperationException()
    }

    // see https://github.com/tachiyomiorg/tachiyomi-extensions/issues/10475
    override fun imageRequest(page: Page): Request {
        val url = page.url.takeIf { it.isNotEmpty() }
        val imageUrl = page.imageUrl!!
        if (url == COMMENTS_FLAG) {
            return GET(imageUrl, headers).newBuilder()
                .tag(CommentsInterceptor.Tag::class, CommentsInterceptor.Tag())
                .build()
        }
        val fallbackUrl = when (preferences.imageQuality) {
            AUTO_RES -> url
            ORIGINAL_RES -> null
            LOW_RES -> if (url == null) null else return GET(url, headers)
            else -> url
        }
        return GET(imageUrl, headers).newBuilder()
            .tag(ImageUrlInterceptor.Tag::class, ImageUrlInterceptor.Tag(fallbackUrl))
            .build()
    }

    // Unused, we can get image urls directly from the chapter page
    override fun imageUrlParse(response: Response) =
        throw UnsupportedOperationException()

    override fun getFilterList() = getFilterListInternal(preferences.isMultiGenreFilter)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        getPreferencesInternal(screen.context).forEach(screen::addPreference)
    }
}
