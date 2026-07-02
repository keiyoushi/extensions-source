package eu.kanade.tachiyomi.extension.ja.fodfuji

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.lib.cookieinterceptor.CookieInterceptor
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

@Source
abstract class FodFuji :
    HttpSource(),
    ConfigurableSource {
    override val supportsLatest = true

    private val apiUrl = "$baseUrl/web/books"
    private val preferences by getPreferencesLazy()

    override fun headersBuilder() = super.headersBuilder()
        .add("Zk-Web-Version", "1.3.5")

    override val client = network.client.newBuilder()
        .addInterceptor(ImageInterceptor())
        .addNetworkInterceptor(CookieInterceptor(baseUrl.toHttpUrl().host, ("sfsc" to "0")))
        .addInterceptor {
            val request = it.request()
            val response = it.proceed(request)
            if (response.code == 403 && request.url.toString().startsWith(apiUrl)) {
                throw IOException("This service is only available in Japan.")
            }
            response
        }
        .build()

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/genreRanking".toHttpUrl().newBuilder()
            .addQueryParameter("category", "0")
            .addQueryParameter("sort_type", "2")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<RankingResponse>()
        val mangas = result.rankingBooks.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/newArrival".toHttpUrl().newBuilder()
            .addQueryParameter("category", "0")
            .addQueryParameter("sort_type", "0")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<LatestResponse>()
        val mangas = result.newArrivalBooks.map { it.toSManga() }
        return MangasPage(mangas, result.hasNextPage())
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("keyword", query)
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SearchResponse>()
        val mangas = result.searchBooks.map { it.toSManga() }
        return MangasPage(mangas, result.searchInfo.hasNextPage())
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val parts = "$baseUrl/${manga.url}".toHttpUrl()
        val bookId = parts.pathSegments.first()
        val episodeId = parts.pathSegments.last()
        val url = "$apiUrl/detail".toHttpUrl().newBuilder()
            .addQueryParameter("book_id", bookId)
            .addQueryParameter("episode_id", episodeId)
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<DetailsResponse>().bookDetail.toSManga()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/books/${manga.url}"

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        return response.parseAs<DetailsResponse>().bookSeries
            .filter { !hideLocked || !it.isLocked }
            .map { it.toSChapter() }
            .reversed()
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/viewer/${chapter.url}"

    override fun pageListRequest(chapter: SChapter): Request {
        val parts = "$baseUrl/${chapter.url}".toHttpUrl()
        val bookId = parts.pathSegments.first()
        val episodeId = parts.pathSegments.last()
        val url = "$apiUrl/licenceKey".toHttpUrl().newBuilder()
            .addQueryParameter("book_id", bookId)
            .addQueryParameter("episode_id", episodeId)
            .build()
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<ViewerResponse>()
        if (result.guardianServer.isNullOrEmpty() || result.bookData == null || result.pagesData == null) {
            throw Exception("Log in via WebView and purchase this product to read.")
        }

        val guardianUrl = "${result.guardianServer}/${result.bookData.s3Key}"
        return if (result.bookData.imagedReflow) {
            parseNovelPages(result, guardianUrl)
        } else {
            parseMangaPages(result, guardianUrl)
        }
    }

    private fun parseNovelPages(result: ViewerResponse, guardianUrl: String): List<Page> {
        val bookJsonUrl = "$guardianUrl/book.json".toHttpUrl().newBuilder()
            .query(result.additionalQueryString)
            .build()

        val book = client.newCall(GET(bookJsonUrl, headers)).execute().parseAs<ReflowBook>()
        val profile = book.reflowData?.profiles?.find { it.id == "mincho_small" }
            ?: book.reflowData?.profiles?.firstOrNull()
            ?: throw Exception("No profile was found.")

        val key = result.pagesData?.keys?.jsonObject?.get(profile.id)?.jsonPrimitive?.content

        return (0 until profile.bookInfo.pageCount).map {
            Page(it, imageUrl = buildPageUrl(guardianUrl, "${profile.id}/${it + 1}.jpg", result.additionalQueryString, key))
        }
    }

    private fun parseMangaPages(result: ViewerResponse, guardianUrl: String): List<Page> {
        val keys = result.pagesData?.keys?.jsonArray?.map { it.jsonPrimitive.content } ?: throw Exception("No keys were found.")

        return keys.mapIndexed { i, key ->
            Page(i, imageUrl = buildPageUrl(guardianUrl, "${i + 1}.jpg", result.additionalQueryString, key))
        }
    }

    private fun buildPageUrl(guardianUrl: String, path: String, signedParams: String?, key: String?): String = "$guardianUrl/$path".toHttpUrl().newBuilder().apply {
        if (!signedParams.isNullOrEmpty()) {
            query(signedParams)
        }
        fragment(key)
    }.build().toString()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
