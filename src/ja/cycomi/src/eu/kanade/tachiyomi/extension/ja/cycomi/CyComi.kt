package eu.kanade.tachiyomi.extension.ja.cycomi

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
import keiyoushi.annotation.Source
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.util.Calendar
import java.util.TimeZone

@Source
abstract class CyComi :
    HttpSource(),
    ConfigurableSource {
    private val domain = "cycomi.com"
    override val supportsLatest = true

    private val apiUrl = "https://web.$domain/api"
    private val jst = TimeZone.getTimeZone("Asia/Tokyo")
    private val preferences by getPreferencesLazy()

    override val client = network.client.newBuilder()
        .addInterceptor(ImageInterceptor())
        .build()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/ranking/title/1", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.extractNextJs<NextData>()?.props?.pageProps?.rankingTitleList.orEmpty().map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val cal = Calendar.getInstance(jst)
        if (cal.get(Calendar.HOUR_OF_DAY) < 12) {
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }

        val dayValue = cal.get(Calendar.DAY_OF_WEEK) - 1
        val url = "$apiUrl/title/serialization/list/$dayValue"
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangas = response.parseAs<Data<MangaResponse>>().data.titles.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = "$apiUrl/search/list/1".toHttpUrl().newBuilder()
                .addQueryParameter("word", query)
                .build()
            return GET(url, headers)
        }

        val filter = filters.firstInstance<CategoryFilter>()
        val url = "$apiUrl/title/serialization/list/${filter.value}"
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = "$apiUrl/title/detail".toHttpUrl().newBuilder()
            .addQueryParameter("titleId", manga.url)
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<Data<DetailsResponse>>().data.toSManga()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title/${manga.url}"

    override fun chapterListRequest(manga: SManga): Request {
        val url = "$apiUrl/chapter/paginatedList".toHttpUrl().newBuilder()
            .addQueryParameter("titleId", manga.url)
            .addQueryParameter("sort", "2")
            .build()
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val titleId = response.request.url.queryParameter("titleId")!!

        fun fetchLockState(path: String): Map<Int, Boolean> {
            val url = "$apiUrl/$path".toHttpUrl().newBuilder()
                .addQueryParameter("titleId", titleId)
                .build()
            return client.newCall(GET(url, headers, CacheControl.FORCE_NETWORK)).execute().parseAs<Data<List<StatusResponse>>>().data.associate { it.id to it.isLocked }
        }

        // server-side rate limit is stricter for non-logged-in users; check if the user is logged in here
        val loggedIn = client.cookieJar.loadForRequest(apiUrl.toHttpUrl()).any { it.name == "sessionId" }
        val statusPaths = if (loggedIn) {
            listOf("user/chapter/status/list", "chapter/status/list")
        } else {
            listOf("chapter/status/list")
        }
        val lockedById = statusPaths
            .firstNotNullOfOrNull { path -> runCatching { fetchLockState(path) }.getOrNull() }
            .orEmpty()

        return response.parseAs<Data<List<ChapterListResponse>>>().data
            .associateWith { lockedById[it.id] ?: false }
            .filter { !hideLocked || !it.value }
            .map { (chapter, isLocked) -> chapter.toSChapter(isLocked) }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/viewer/chapter/${chapter.url}"

    override fun pageListRequest(chapter: SChapter): Request {
        val url = "$baseUrl/${chapter.url}".toHttpUrl()
        val titleId = url.fragment!!.toInt()
        val chapterId = url.pathSegments.first().toInt()
        val body = ViewerRequestBody(titleId, chapterId).toJsonRequestBody()
        return POST("$apiUrl/chapter/page/list", headers, body)
    }

    override fun pageListParse(response: Response): List<Page> {
        val results = response.parseAs<Data<ViewerResponse>>()
        if (results.data.pages.isEmpty()) {
            throw Exception("Log in via WebView and rent or purchase this chapter.")
        }

        return results.data.pages.map {
            Page(it.pageNumber, imageUrl = "${it.image}#decrypt")
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Paid Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        CategoryFilter(),
    )

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
