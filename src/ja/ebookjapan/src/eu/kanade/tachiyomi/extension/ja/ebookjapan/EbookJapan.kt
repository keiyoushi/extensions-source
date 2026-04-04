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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.TimeZone

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
    private val preferences: SharedPreferences by getPreferencesLazy()
    private val perPage = 50

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

    // Viewer
    // low res images (older browser): https://ebookjapan.yahoo.co.jp/br_api/open_light {"type":"story","code":"B00163893575","ssid":"123665","light":true}
    // https://ebookjapan.yahoo.co.jp/br_api/light_image/c43293a1f1284f84b715facdd3bfa4b5/1
    override fun pageListRequest(chapter: SChapter): Request {
        val url = "$baseUrl/${chapter.url}".toHttpUrl()
        val serialStoryId = url.fragment!!
        val bookCd = url.pathSegments.first()
        val body = ViewerBody("story", bookCd, serialStoryId, false).toJsonString().toRequestBody("application/json".toMediaType())
        return POST("$viewerUrl/open_book", headers, body)
    }

    override fun pageListParse(response: Response): List<Page> {
        val sessionId = response.parseAs<ViewerOpenBook>().sessionId
        val drmUrl = "$viewerUrl/get_drm".toHttpUrl().newBuilder()
            .addQueryParameter("session_id", sessionId)
            .build()
        val drmRequest = GET(drmUrl, headers)
        val drmResponse = client.newCall(drmRequest).execute()
        val drmResult = drmResponse.parseAs<ViewerDrmResponse>()
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
    }
}
