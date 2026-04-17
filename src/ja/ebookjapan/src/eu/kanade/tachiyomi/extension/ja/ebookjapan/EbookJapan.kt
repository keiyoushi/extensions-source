package eu.kanade.tachiyomi.extension.ja.ebookjapan

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class EbookJapan :
    HttpSource(),
    ConfigurableSource {
    override val name = "eBookJapan"
    override val lang = "ja"
    override val baseUrl = "https://ebookjapan.yahoo.co.jp"
    override val supportsLatest = false

    private val apiUrl = "$baseUrl/proxy/apis"
    private val cdnUrl = "https://cache2-ebookjapan.akamaized.net/contents/thumb/l"
    private val viewerUrl = "$baseUrl/br_api"
    private val viewerCdnUrl = "https://prod-contents-br-page.akamaized.net"
    private val preferences: SharedPreferences by getPreferencesLazy()
    private val perPage = 50
    private val decoder = Decoder()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
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

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        TODO()
    }

    override fun searchMangaParse(response: Response): MangasPage {
        TODO()
    }

    // Details
    override fun getMangaUrl(manga: SManga) = "$baseUrl/books/${manga.url}/"

    override fun mangaDetailsRequest(manga: SManga) = GET("$apiUrl/books/titleV2/sync?titleId=${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<DetailResponse>().title.toSManga(cdnUrl)

    // Chapters
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val serialStoryId = response.parseAs<DetailResponse>().serialStory.serialStoryId
        val requestUrl = "$apiUrl/books/titleV2/storyList".toHttpUrl().newBuilder()
            .addQueryParameter("serialStoryId", serialStoryId)
            .addQueryParameter("start", "0")
            .addQueryParameter("results", "9999")
            .addQueryParameter("sort", "asc")
            .addQueryParameter("isSortAsc", "0")
            .build()
        val request = GET(requestUrl, headers)
        val chapterResponse = client.newCall(request).execute()
        val result = chapterResponse.parseAs<ChapterResponse>()
        return result.stories.filter { !hideLocked || !it.isLocked }.map { it.toSChapter() }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val url = "$baseUrl/${chapter.url}".toHttpUrl()
        val serialStoryId = url.fragment
        val bookCd = url.pathSegments.first()
        return "$baseUrl/viewer/story/$bookCd/?ssid=$serialStoryId"
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val url = "$baseUrl/${chapter.url}".toHttpUrl()
        val serialStoryId = url.fragment!!
        val bookCd = url.pathSegments.first()
        val body = ViewerBody("story", bookCd, serialStoryId, false).toJsonString().toRequestBody(JSON_MEDIA_TYPE)
        return POST("$viewerUrl/open_book", headers, body)
    }

    override fun pageListParse(response: Response): List<Page> {
        val originalRequest = response.request
        var currentResponse = response

        repeat(MAX_RETRIES) { attempt ->
            try {
                return buildPages(currentResponse)
            } catch (_: Throwable) {
                if (attempt < MAX_RETRIES - 1) {
                    Thread.sleep(RETRY_DELAY_MS)
                    currentResponse = client.newCall(originalRequest).execute()
                }
            }
        }
        throw Exception("Too bad, try again.")
    }

    private fun buildPages(response: Response): List<Page> {
        val openBook = response.parseAs<ViewerOpenBook>()

        val drmUrl = "$viewerUrl/get_drm".toHttpUrl().newBuilder()
            .addQueryParameter("session_id", openBook.sessionId)
            .build()

        val drmResult = client.newCall(GET(drmUrl, headers)).execute().parseAs<ViewerDrmResponse>()

        decoder.decryptSession(
            sessionId = openBook.sessionId,
            code = drmResult.code,
            openPayload = openBook.payload,
            drmPayload = drmResult.payload,
            fileId = drmResult.fileId,
        )

        return (0 until decoder.pageCount()).map { index ->
            val url = "$viewerCdnUrl/pages/${decoder.getPageName(index)}".toHttpUrl().newBuilder()
                .fragment("data=${decoder.encodeScrambleFragment(index)}")
                .build()
                .toString()

            Page(index, imageUrl = url)
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
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        private const val MAX_RETRIES = 10
        private const val RETRY_DELAY_MS = 1500L
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
