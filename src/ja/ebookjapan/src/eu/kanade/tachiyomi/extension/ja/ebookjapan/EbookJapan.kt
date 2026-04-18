package eu.kanade.tachiyomi.extension.ja.ebookjapan

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.lib.cookieinterceptor.CookieInterceptor
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rx.Observable
import java.io.IOException

class EbookJapan :
    HttpSource(),
    ConfigurableSource {
    override val name = "eBookJapan"
    override val lang = "ja"
    private val domain = "ebookjapan.yahoo.co.jp"
    override val baseUrl = "https://$domain"
    override val supportsLatest = true

    private val apiUrl = "$baseUrl/proxy/apis"
    private val cdnUrl = "https://cache2-ebookjapan.akamaized.net/contents/thumb/l"
    private val viewerUrl = "$baseUrl/br_api"
    private val viewerCdnUrl = "https://prod-contents-br-page.akamaized.net"
    private val preferences: SharedPreferences by getPreferencesLazy()
    private val perPage = 50

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
        .addNetworkInterceptor(CookieInterceptor(domain, "ebaf" to "1"))
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if ((response.code == 400 || response.code == 404) && request.url.pathSegments[1] == "open_book") {
                throw IOException("Log in via WebView and purchase this product to read.")
            }
            response
        }
        .build()

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        val start = (page - 1) * perPage
        val url = "$apiUrl/ranking/details".toHttpUrl().newBuilder()
            .addQueryParameter("type", "charge")
            .addQueryParameter("term", "recent")
            .addQueryParameter("start", start.toString())
            .addQueryParameter("results", perPage.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<RankingResponse>()
        val mangas = result.rankingPublications.items.map { it.toSManga(cdnUrl) }
        val start = response.request.url.queryParameter("start")!!.toInt()
        val total = result.rankingPublications.totalResults
        val hasNextPage = start + perPage < total
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val start = (page - 1) * perPage
        val url = "$apiUrl/recent/details".toHttpUrl().newBuilder()
            .addQueryParameter("useTitle", "0")
            .addQueryParameter("start", start.toString())
            .addQueryParameter("results", perPage.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<Publications>()
        val mangas = result.items.map { it.toSManga(cdnUrl) }
        val start = response.request.url.queryParameter("start")!!.toInt()
        val hasNextPage = start + perPage < result.totalResults
        return MangasPage(mangas, hasNextPage)
    }

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val start = (page - 1) * perPage
        val url = "$apiUrl/search/titles".toHttpUrl().newBuilder()
            .addQueryParameter("keyword", query)
            .addQueryParameter("start", start.toString())
            .addQueryParameter("results", perPage.toString())
            .addQueryParameter("sort", "weeklyPurchasedRanking")
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SearchResponse>()
        val mangas = result.items.map { it.toSManga(cdnUrl) }
        val start = response.request.url.queryParameter("start")!!.toInt()
        val hasNextPage = start + perPage < result.totalResults
        return MangasPage(mangas, hasNextPage)
    }

    // Details
    override fun getMangaUrl(manga: SManga) = "$baseUrl/books/${manga.url}/"

    override fun mangaDetailsRequest(manga: SManga) = GET("$apiUrl/books/titleV2/sync?titleId=${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<DetailResponse>().title.toSManga(cdnUrl)

    // Chapters
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chaptersResponse = client.newCall(mangaDetailsRequest(manga)).asObservableSuccess()
            .flatMap { response ->
                val serialStory = response.parseAs<DetailResponse>().serialStory
                    ?: return@flatMap Observable.just(emptyList())
                val requestUrl = "$apiUrl/books/titleV2/storyList".toHttpUrl().newBuilder()
                    .addQueryParameter("serialStoryId", serialStory.serialStoryId)
                    .addQueryParameter("start", "0")
                    .addQueryParameter("results", "9999")
                    .addQueryParameter("sort", "asc")
                    .addQueryParameter("isSortAsc", "0")
                    .build()
                client.newCall(GET(requestUrl, headers)).asObservableSuccess()
                    .map { it.parseAs<ChapterResponse>().stories }
            }
            .map { stories ->
                stories.filter { !hideLocked || !it.isLocked }.map { it.toSChapter() }
            }

        val volumeUrl = "$apiUrl/books/titleV2/publicationList".toHttpUrl().newBuilder()
            .addQueryParameter("titleId", manga.url)
            .build()

        val volumesResponse = client.newCall(GET(volumeUrl, headers)).asObservableSuccess()
            .map { response ->
                response.parseAs<VolumesResponse>().publications
                    .filter { !hideLocked || !it.isLocked }
                    .reversed()
                    .map { it.toSChapter() }
            }

        return Observable.zip(chaptersResponse, volumesResponse) { chapters, volumes -> chapters + volumes }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val url = "$baseUrl/${chapter.url}".toHttpUrl()
        val bookCd = url.pathSegments.first()
        val serialStoryId = url.fragment
        return if (serialStoryId != null) {
            "$baseUrl/viewer/story/$bookCd/?ssid=$serialStoryId"
        } else {
            "$baseUrl/viewer/free/$bookCd/"
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val url = "$baseUrl/${chapter.url}".toHttpUrl()
        val bookCd = url.pathSegments.first()
        val serialStoryId = url.fragment
        val body = if (serialStoryId != null) {
            ViewerBody("story", bookCd, serialStoryId, false).toJsonString()
        } else {
            ViewerVolumeBody("free", bookCd, false).toJsonString()
        }.toRequestBody(JSON_MEDIA_TYPE)
        return POST("$viewerUrl/open_book", headers, body)
    }

    override fun pageListParse(response: Response): List<Page> {
        val openBook = response.parseAs<ViewerOpenBook>()
        val drmUrl = "$viewerUrl/get_drm".toHttpUrl().newBuilder()
            .addQueryParameter("session_id", openBook.sessionId)
            .build()

        val drmResult = client.newCall(GET(drmUrl, headers)).execute().parseAs<ViewerDrmResponse>()
        val book = Decoder().decryptSession(
            sessionId = openBook.sessionId,
            code = drmResult.code,
            openPayload = openBook.payload,
            drmPayload = drmResult.payload,
            fileId = drmResult.fileId,
        )

        return (0 until book.pageCount()).map {
            val url = "$viewerCdnUrl/pages/${book.getPageName(it)}".toHttpUrl().newBuilder()
                .fragment("data=${book.encodeFragment(it)}")
                .build()
                .toString()

            Page(it, imageUrl = url)
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    // Unsupported
    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
