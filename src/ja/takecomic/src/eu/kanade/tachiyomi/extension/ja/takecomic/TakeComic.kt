package eu.kanade.tachiyomi.extension.ja.takecomic

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.comiciviewer.ComiciViewer
import eu.kanade.tachiyomi.multisrc.comiciviewer.ViewerResponse
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import kotlin.getValue

class TakeComic : ComiciViewer(
    "TakeComic",
    "https://takecomic.jp",
    "ja",
) {
    private val apiUrl = "$baseUrl/api"
    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun popularMangaParse(response: Response): MangasPage {
        return latestUpdatesParse(response)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/series/list/up/$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.series-list-item").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a.series-list-item-link")!!.attr("href"))
                title = element.selectFirst("div.series-list-item-h span")!!.text()
                thumbnail_url = element.selectFirst("img.series-list-item-img")?.attr("src")?.let { baseUrl.toHttpUrlOrNull()?.newBuilder(it)?.build()?.queryParameter("url") }
            }
        }
        val hasNextPage = document.selectFirst("a.g-pager-link.mode-active + a.g-pager-link") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$apiUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("page", page.toString())
                .addQueryParameter("size", SEARCH_PAGE_SIZE.toString())
                .build()
            return GET(url, headers)
        }

        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val browseFilter = filterList.firstInstance<BrowseFilter>()
        val path = getFilterOptions()[browseFilter.state].second

        val url = if (path == "/ranking/manga") {
            "$baseUrl$path"
        } else {
            "$baseUrl$path/$page"
        }
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val url = response.request.url.toString()
        if (url.contains("/api/search")) {
            val result = response.parseAs<SearchApiResponse>().searchResult.series
            val mangas = result.series.map { it.toSManga() }
            val page = response.request.url.queryParameter("page")!!.toInt()
            val hasNextPage = result.total > page * SEARCH_PAGE_SIZE
            return MangasPage(mangas, hasNextPage)
        }
        return latestUpdatesParse(response)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val seriesHash = response.request.url.pathSegments.last()
        val apiUrl = "$apiUrl/episodes".toHttpUrl().newBuilder()
            .addQueryParameter("seriesHash", seriesHash)
            .build()
        val apiRequest = GET(apiUrl, headers)
        val apiResponse = client.newCall(apiRequest).execute()
        return apiResponse.parseAs<ApiResponse>().series.summary.toSManga(seriesHash)
    }

    override fun chapterListRequest(manga: SManga): Request {
        val seriesHash = manga.url.substringAfterLast("/")
        val apiUrl = "$apiUrl/episodes".toHttpUrl().newBuilder()
            .addQueryParameter("seriesHash", seriesHash)
            .addQueryParameter("episodeFrom", "1")
            .addQueryParameter("episodeTo", "9999")
            .build()
        return GET(apiUrl, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val apiResponse = response.parseAs<ApiResponse>()
        val seriesHash = response.request.url.queryParameter("seriesHash")!!

        val accessUrl = "$apiUrl/series/access".toHttpUrl().newBuilder()
            .addQueryParameter("seriesHash", seriesHash)
            .addQueryParameter("episodeFrom", "1")
            .addQueryParameter("episodeTo", "9999")
            .build()
        val accessRequest = GET(accessUrl, headers, CacheControl.FORCE_NETWORK)
        val accessResponse = client.newCall(accessRequest).execute()
        val accessMap = accessResponse.parseAs<AccessApiResponse>().seriesAccess.episodeAccesses
            .associateBy { it.episodeId }

        val showLocked = preferences.getBoolean(SHOW_LOCKED_PREF_KEY, true)
        val showCampaignLocked = preferences.getBoolean(SHOW_CAMPAIGN_LOCKED_PREF_KEY, true)

        return apiResponse.series.toSChapter(accessMap, showLocked, showCampaignLocked).reversed()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.endsWith(LOGIN_SUFFIX)) {
            throw Exception("This chapter is free but you need to log in via WebView and refresh the entry")
        }
        return super.pageListRequest(chapter)
    }

    override fun pageListParse(response: Response): List<Page> {
        val episodeId = response.request.url.pathSegments.last()

        var comiciViewerId: String? = null
        var memberJwt: String? = null

        try {
            val apiUrl = "$apiUrl/episodes/$episodeId"
            val accessRequest = GET(apiUrl, headers, CacheControl.FORCE_NETWORK)
            val apiResponse = client.newCall(accessRequest).execute()
            if (apiResponse.isSuccessful) {
                comiciViewerId = apiResponse.parseAs<EpisodeDetailsApiResponse>()
                    .episode.content
                    .firstOrNull { it.type == "viewer" }?.viewerId
            }
        } catch (e: Exception) { comiciViewerId = null }

        if (comiciViewerId == null) {
            val document = response.asJsoup()
            val viewer = document.selectFirst("#comici-viewer") ?: throw Exception("Log in via WebView and purchase this chapter to read")
            comiciViewerId = viewer.attr("data-comici-viewer-id")
            memberJwt = viewer.attr("data-member-jwt")
        }

        val userId = try {
            val userInfoResponse = client.newCall(GET("$apiUrl/user/info", headers)).execute()
            userInfoResponse.parseAs<UserInfoApiResponse>().user?.id
        } catch (e: Exception) {
            memberJwt
        }

        val requestUrl = "$apiUrl/book/contentsInfo".toHttpUrl().newBuilder()
            .addQueryParameter("comici-viewer-id", comiciViewerId)
            .addQueryParameter("user-id", userId)
            .addQueryParameter("page-from", "0")

        val pageTo = client.newCall(GET(requestUrl.addQueryParameter("page-to", "1").build(), headers))
            .execute().use { initialResponse ->
                if (!initialResponse.isSuccessful) {
                    throw Exception("Failed to get page list. HTTP ${initialResponse.code}")
                }
                initialResponse.parseAs<ViewerResponse>().totalPages.toString()
            }

        val getAllPagesUrl = requestUrl.setQueryParameter("page-to", pageTo).build()
        return client.newCall(GET(getAllPagesUrl, headers)).execute().use { allPagesResponse ->
            if (allPagesResponse.isSuccessful) {
                allPagesResponse.parseAs<ViewerResponse>().result.map { resultItem ->
                    val urlBuilder = resultItem.imageUrl.toHttpUrl().newBuilder()
                    if (resultItem.scramble.isNotEmpty()) {
                        urlBuilder.addQueryParameter("scramble", resultItem.scramble)
                    }
                    Page(
                        index = resultItem.sort,
                        imageUrl = urlBuilder.build().toString(),
                    )
                }.sortedBy { it.index }
            } else {
                throw Exception("Failed to get full page list")
            }
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        super.setupPreferenceScreen(screen)
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_CAMPAIGN_LOCKED_PREF_KEY
            title = "Show 'Require Login' chapters"
            summary = "Shows chapters that are free but require login"
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    override fun getFilterOptions(): List<Pair<String, String>> = listOf(
        Pair("ランキング", "/ranking/manga"),
        Pair("更新順", "/series/list/up"),
        Pair("新作順", "/series/list/new"),
        Pair("完結", "/category/manga/complete"),
        Pair("月曜日", "/category/manga/day/1"),
        Pair("火曜日", "/category/manga/day/2"),
        Pair("水曜日", "/category/manga/day/3"),
        Pair("木曜日", "/category/manga/day/4"),
        Pair("金曜日", "/category/manga/day/5"),
        Pair("土曜日", "/category/manga/day/6"),
        Pair("日曜日", "/category/manga/day/7"),
        Pair("その他", "/category/manga/day/8"),
    )

    companion object {
        private const val SEARCH_PAGE_SIZE = 24
        private const val SHOW_LOCKED_PREF_KEY = "pref_show_locked_chapters"
        private const val SHOW_CAMPAIGN_LOCKED_PREF_KEY = "pref_show_campaign_locked_chapters"
        const val LOGIN_SUFFIX = "#LOGIN"
    }
}
