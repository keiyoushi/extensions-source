package eu.kanade.tachiyomi.multisrc.comiciviewer

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

abstract class ComiciViewerAlt(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    private val apiUrl: String,
) : ConfigurableSource, HttpSource() {
    private val preferences: SharedPreferences by getPreferencesLazy()

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/ranking/manga", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        return latestUpdatesParse(response)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/category/manga", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.series-list-item").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.selectFirst("a.series-list-item-link")!!.absUrl("href"))
                title = it.selectFirst("div.series-list-item-h span")!!.text()
                thumbnail_url = it.selectFirst("img.series-list-item-img")?.absUrl("src")
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
        val url = response.request.url.pathSegments
        if (url.contains("api") && url.contains("search")) {
            val result = response.parseAs<SearchApiResponse>().searchResult.series
            val mangas = result.series.map { it.toSManga() }
            val page = response.request.url.queryParameter("page")!!.toInt()
            val hasNextPage = result.total > page * SEARCH_PAGE_SIZE
            return MangasPage(mangas, hasNextPage)
        }
        return latestUpdatesParse(response)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val seriesHash = (baseUrl + manga.url).toHttpUrl().pathSegments.last()
        val episodes = "$apiUrl/episodes".toHttpUrl().newBuilder()
            .addQueryParameter("seriesHash", seriesHash)
            .build()
        return GET(episodes, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val seriesHash = response.request.url.pathSegments.last()
        return response.parseAs<ApiResponse>().series.summary.toSManga(seriesHash)
    }

    override fun getMangaUrl(manga: SManga): String {
        return baseUrl + manga.url
    }

    override fun chapterListRequest(manga: SManga): Request {
        val seriesHash = (baseUrl + manga.url).toHttpUrl().pathSegments.last()
        val url = "$apiUrl/episodes".toHttpUrl().newBuilder()
            .addQueryParameter("seriesHash", seriesHash)
            .addQueryParameter("episodeFrom", "1")
            .addQueryParameter("episodeTo", "9999")
            .build()
        return GET(url, headers, CacheControl.FORCE_NETWORK)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val showLocked = preferences.getBoolean(SHOW_LOCKED_PREF_KEY, true)
        val showCampaignLocked = preferences.getBoolean(SHOW_CAMPAIGN_LOCKED_PREF_KEY, true)
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

        return apiResponse.series.toSChapter(accessMap, showLocked, showCampaignLocked).reversed()
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return baseUrl + chapter.url
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.endsWith(LOGIN_SUFFIX)) {
            throw Exception("This chapter is free but you need to log in via WebView and refresh the entry.")
        }

        val episodeId = (baseUrl + chapter.url).toHttpUrl().pathSegments.last()
        return GET("$apiUrl/episodes/$episodeId", headers, CacheControl.FORCE_NETWORK)
    }

    override fun pageListParse(response: Response): List<Page> {
        val comiciViewerId = response.parseAs<EpisodeDetailsApiResponse>().episode.content
            .first { it.type == "viewer" }.viewerId

        val memberJwt = try {
            val userInfoResponse = client.newCall(GET("$apiUrl/user/info", headers, CacheControl.FORCE_NETWORK)).execute()
            userInfoResponse.parseAs<UserInfoApiResponse>().user?.id
        } catch (_: Exception) {
            null
        }

        val requestUrl = "$apiUrl/book/contentsInfo".toHttpUrl().newBuilder()
            .addQueryParameter("comici-viewer-id", comiciViewerId)
            .addQueryParameter("user-id", memberJwt)
            .addQueryParameter("page-from", "0")

        val getPages = requestUrl.addQueryParameter("page-to", "1").build()
        val pageToRequest = client.newCall(GET(getPages, headers)).execute()
        val pageToParse = try {
            pageToRequest.parseAs<ViewerResponse>().totalPages.toString()
        } catch (_: Exception) {
            throw Exception("Log in via WebView and purchase this chapter to read.")
        }

        val getAllPages = requestUrl.setQueryParameter("page-to", pageToParse).build()
        val pages = client.newCall(GET(getAllPages, headers)).execute()

        return pages.parseAs<ViewerResponse>().result.map {
            val url = it.imageUrl.toHttpUrl().newBuilder()
                .fragment(it.scramble)
                .build()

            Page(it.sort, imageUrl = url.toString())
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_LOCKED_PREF_KEY
            title = "Show locked chapters"
            setDefaultValue(true)
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_CAMPAIGN_LOCKED_PREF_KEY
            title = "Show 'Require Login' chapters"
            summary = "Shows chapters that are free but require login"
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    protected open class BrowseFilter(vals: Array<String>) : Filter.Select<String>("Filter by", vals)

    protected open fun getFilterOptions(): List<Pair<String, String>> = listOf(
        Pair("ランキング", "/ranking/manga"),
        Pair("更新順", "/series/list/up"),
        Pair("新作順", "/series/list/new"),
        Pair("読み切り", "/category/manga/oneShot"),
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

    override fun getFilterList() = FilterList(
        BrowseFilter(getFilterOptions().map { it.first }.toTypedArray()),
    )

    // Unsupported
    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    companion object {
        private const val SEARCH_PAGE_SIZE = 24
        private const val SHOW_LOCKED_PREF_KEY = "pref_show_locked_chapters"
        private const val SHOW_CAMPAIGN_LOCKED_PREF_KEY = "pref_show_campaign_locked_chapters"
        const val LOGIN_SUFFIX = "#LOGIN"
    }
}
