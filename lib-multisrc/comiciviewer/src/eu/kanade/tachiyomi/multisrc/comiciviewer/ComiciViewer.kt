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
import keiyoushi.utils.tryParse
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

abstract class ComiciViewer(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    private val apiUrl: String? = null,
) : ConfigurableSource, HttpSource() {
    private val preferences: SharedPreferences by getPreferencesLazy()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.JAPAN)

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/ranking/manga", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        if (apiUrl != null) {
            return latestUpdatesParse(response)
        } else {
            val document = response.asJsoup()
            val mangas = document.select("div.ranking-box-vertical, div.ranking-box-vertical-top3")
                .map { element ->
                    SManga.create().apply {
                        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                        title = element.selectFirst(".title-text")!!.text()
                        thumbnail_url =
                            element.selectFirst("source")?.attr("data-srcset")?.substringBefore(" ")
                                ?.let { "https:$it" }
                    }
                }
            return MangasPage(mangas, false)
        }
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/category/manga", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        if (apiUrl != null) {
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
        } else {
            val document = response.asJsoup()
            val mangas = document.select("div.category-box-vertical").map { element ->
                SManga.create().apply {
                    setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                    title = element.selectFirst(".title-text")!!.text()
                    thumbnail_url =
                        element.selectFirst("source")?.attr("data-srcset")?.substringBefore(" ")
                            ?.let { "https:$it" }
                }
            }
            return MangasPage(mangas, false)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            if (apiUrl != null) {
                val url = "$apiUrl/search".toHttpUrl().newBuilder()
                    .addQueryParameter("q", query)
                    .addQueryParameter("page", page.toString())
                    .addQueryParameter("size", SEARCH_PAGE_SIZE.toString())
                    .build()
                return GET(url, headers)
            } else {
                val url = "$baseUrl/search".toHttpUrl().newBuilder()
                    .addQueryParameter("keyword", query)
                    .addQueryParameter("page", (page - 1).toString())
                    .addQueryParameter("filter", "series")
                    .build()
                return GET(url, headers)
            }
        }
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val browseFilter = filterList.firstInstance<BrowseFilter>()
        val pathAndQuery = getFilterOptions()[browseFilter.state].second

        val url = if (apiUrl != null && pathAndQuery == "/ranking/manga") {
            "$baseUrl$pathAndQuery"
        } else if (apiUrl != null) {
            "$baseUrl$pathAndQuery/$page"
        } else {
            (baseUrl + pathAndQuery).toHttpUrl().newBuilder().build().toString()
        }

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val url = response.request.url.pathSegments

        if (apiUrl != null && url.contains("api") && url.contains("search")) {
            val result = response.parseAs<SearchApiResponse>().searchResult.series
            val mangas = result.series.map { it.toSManga() }
            val page = response.request.url.queryParameter("page")!!.toInt()
            val hasNextPage = result.total > page * SEARCH_PAGE_SIZE
            return MangasPage(mangas, hasNextPage)
        }

        return when {
            url.contains("ranking") -> popularMangaParse(response)
            url.contains("category") || (url.contains("series") && url.contains("list")) -> latestUpdatesParse(response)

            else -> {
                val document = response.asJsoup()
                val mangas = document.select("div.manga-store-item").map { element ->
                    SManga.create().apply {
                        setUrlWithoutDomain(
                            element.selectFirst("a.c-ms-clk-article")!!.absUrl("href"),
                        )
                        title = element.selectFirst("h2.manga-title")!!.text()
                        thumbnail_url =
                            element.selectFirst("source")?.attr("data-srcset")?.substringBefore(" ")
                                ?.let { "https:$it" }
                    }
                }
                val hasNextPage = document.selectFirst("li.mode-paging-active + li > a") != null
                return MangasPage(mangas, hasNextPage)
            }
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        if (apiUrl != null) {
            val seriesHash = response.request.url.pathSegments.last()
            val apiUrl = "$apiUrl/episodes".toHttpUrl().newBuilder()
                .addQueryParameter("seriesHash", seriesHash)
                .build()
            val apiRequest = GET(apiUrl, headers)
            val apiResponse = client.newCall(apiRequest).execute()
            return apiResponse.parseAs<ApiResponse>().series.summary.toSManga(seriesHash)
        } else {
            val document = response.asJsoup()
            return SManga.create().apply {
                title = document.select("h1.series-h-title span").last()!!.text()
                author = document.select("div.series-h-credit-user").text()
                artist = author
                description = document.selectFirst("div.series-h-credit-info-text-text")?.text()
                genre = document.select("a.series-h-tag-link").joinToString { it.text().removePrefix("#") }
                thumbnail_url = document.selectFirst("div.series-h-img source")?.attr("data-srcset")?.substringBefore(" ")?.let { "https:$it" }
            }
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        if (apiUrl != null) {
            val seriesHash = (baseUrl + manga.url).toHttpUrl().pathSegments.last()
            val apiRequestUrl = "$apiUrl/episodes".toHttpUrl().newBuilder()
                .addQueryParameter("seriesHash", seriesHash)
                .addQueryParameter("episodeFrom", "1")
                .addQueryParameter("episodeTo", "9999")
                .build()
            return GET(apiRequestUrl, headers)
        } else {
            val url = "$baseUrl${manga.url}/list".toHttpUrl().newBuilder()
                .addQueryParameter("s", "1")
                .build()
            return GET(url, headers)
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val showLocked = preferences.getBoolean(SHOW_LOCKED_PREF_KEY, true)

        if (apiUrl != null) {
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

            val showCampaignLocked = preferences.getBoolean(SHOW_CAMPAIGN_LOCKED_PREF_KEY, true)

            return apiResponse.series.toSChapter(accessMap, showLocked, showCampaignLocked).reversed()
        } else {
            val document = response.asJsoup()
            return document.select("div.series-ep-list-item").mapNotNull {
                val link = it.selectFirst("a.g-episode-link-wrapper")!!

                val isFree = it.selectFirst("span.free-icon-new") != null
                val isTicketLocked = it.selectFirst("img[data-src*='free_charge_ja.svg']") != null
                val isCoinLocked = it.selectFirst("img[data-src*='coin.svg']") != null
                val isLocked = !isFree

                if (!showLocked && isLocked) {
                    return@mapNotNull null
                }

                SChapter.create().apply {
                    val chapterUrl = link.absUrl("data-href")
                    if (chapterUrl.isNotEmpty()) {
                        setUrlWithoutDomain(chapterUrl)
                    } else {
                        url = response.request.url.toString() + "#" + link.absUrl("data-article") + DUMMY_URL_SUFFIX
                    }

                    name = link.selectFirst("span.series-ep-list-item-h-text")!!.text()
                    when {
                        isTicketLocked -> name = "🔒 $name"
                        isCoinLocked -> name = "\uD83E\uDE99 $name"
                    }

                    date_upload = dateFormat.tryParse(it.selectFirst("time")?.attr("datetime"))
                }
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.endsWith(DUMMY_URL_SUFFIX)) {
            throw Exception("Log in via WebView to read purchased chapters and refresh the entry")
        }
        if (chapter.url.endsWith(LOGIN_SUFFIX)) {
            throw Exception("This chapter is free but you need to log in via WebView and refresh the entry")
        }
        return super.pageListRequest(chapter)
    }

    override fun pageListParse(response: Response): List<Page> {
        val apiHeaders = super.headersBuilder()
            .set("Referer", response.request.url.toString())
            .build()

        var comiciViewerId: String? = null
        var memberJwt: String? = null

        if (apiUrl != null) {
            val episodeId = response.request.url.pathSegments.last()
            try {
                val episodeApiUrl = "$apiUrl/episodes/$episodeId"
                val accessRequest = GET(episodeApiUrl, apiHeaders, CacheControl.FORCE_NETWORK)
                val apiResponse = client.newCall(accessRequest).execute()
                if (apiResponse.isSuccessful) {
                    comiciViewerId = apiResponse.parseAs<EpisodeDetailsApiResponse>()
                        .episode.content
                        .firstOrNull { it.type == "viewer" }?.viewerId
                }
            } catch (_: Exception) {
                comiciViewerId = null
            }
        }

        if (comiciViewerId == null) {
            val document = response.asJsoup()
            val viewer = document.selectFirst("#comici-viewer")
                ?: throw Exception("You need to log in via WebView to read this chapter or purchase this chapter")

            comiciViewerId = if (apiUrl != null) viewer.attr("data-comici-viewer-id") else viewer.attr("comici-viewer-id")
            memberJwt = viewer.attr("data-member-jwt")
        }

        val userId = if (apiUrl != null) {
            try {
                val userInfoResponse = client.newCall(GET("$apiUrl/user/info", apiHeaders)).execute()
                userInfoResponse.parseAs<UserInfoApiResponse>().user?.id
            } catch (_: Exception) {
                memberJwt
            }
        } else {
            memberJwt
        }

        val baseContentUrl = apiUrl ?: baseUrl
        val requestUrl = "$baseContentUrl/book/contentsInfo".toHttpUrl().newBuilder()
            .addQueryParameter("comici-viewer-id", comiciViewerId)
            .addQueryParameter("user-id", userId)
            .addQueryParameter("page-from", "0")

        val pageTo = client.newCall(GET(requestUrl.addQueryParameter("page-to", "1").build(), apiHeaders)).execute()
        val pageToResponse = pageTo.parseAs<ViewerResponse>().totalPages.toString()
        val getAllPagesUrl = requestUrl.setQueryParameter("page-to", pageToResponse).build()
        val getAllPagesRequest = client.newCall(GET(getAllPagesUrl, apiHeaders)).execute()
        return getAllPagesRequest.parseAs<ViewerResponse>().result.map {
            val urlBuilder = it.imageUrl.toHttpUrl().newBuilder()
            if (it.scramble.isNotEmpty()) {
                urlBuilder.fragment(it.scramble)
            }
            Page(it.sort, imageUrl = urlBuilder.build().toString())
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_LOCKED_PREF_KEY
            title = "Show locked chapters"
            setDefaultValue(true)
        }.also(screen::addPreference)

        if (apiUrl != null) {
            SwitchPreferenceCompat(screen.context).apply {
                key = SHOW_CAMPAIGN_LOCKED_PREF_KEY
                title = "Show 'Require Login' chapters"
                summary = "Shows chapters that are free but require login"
                setDefaultValue(true)
            }.also(screen::addPreference)
        }
    }

    protected open class BrowseFilter(vals: Array<String>) : Filter.Select<String>("Filter by", vals)

    protected open fun getFilterOptions(): List<Pair<String, String>> = listOf(
        Pair("ランキング", "/ranking/manga"),
        Pair("読み切り", "/category/manga?type=読み切り"),
        Pair("完結", "/category/manga?type=完結"),
        Pair("月曜日", "/category/manga?type=連載中&day=月"),
        Pair("火曜日", "/category/manga?type=連載中&day=火"),
        Pair("水曜日", "/category/manga?type=連載中&day=水"),
        Pair("木曜日", "/category/manga?type=連載中&day=木"),
        Pair("金曜日", "/category/manga?type=連載中&day=金"),
        Pair("土曜日", "/category/manga?type=連載中&day=土"),
        Pair("日曜日", "/category/manga?type=連載中&day=日"),
        Pair("その他", "/category/manga?type=連載中&day=その他"),
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
        private const val DUMMY_URL_SUFFIX = "NeedLogin"
        const val LOGIN_SUFFIX = "#LOGIN"
    }
}
