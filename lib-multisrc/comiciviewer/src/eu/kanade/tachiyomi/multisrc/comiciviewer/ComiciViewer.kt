package eu.kanade.tachiyomi.multisrc.comiciviewer

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.boolean
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

abstract class ComiciViewer :
    KeiSource(),
    ConfigurableSource {
    protected open val apiUrl get() = "$baseUrl/api"
    protected open val preferences by getPreferencesLazy()
    protected open val rscHeaders get() = headersBuilder()
        .set("rsc", "1")
        .build()

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(ImageInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage {
        val result = client.get("$baseUrl$RANKING_PATH", rscHeaders).extractNextJs<RankingResponse> {
            it is JsonObject && (it["className"] as? JsonPrimitive)?.content == "series-list mode-ranking"
        }

        val mangas = result?.children.orEmpty().map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int) = client.get("$baseUrl/series/list/up/$page").toMangasPage()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$apiUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("page", page.toString())
                .addQueryParameter("size", SEARCH_PAGE_SIZE.toString())
                .build()

            val result = client.get(url).parseAs<SearchApiResponse>().searchResult.series
            val mangas = result.series.map { it.toSManga() }
            val hasNextPage = result.total > page * SEARCH_PAGE_SIZE
            return MangasPage(mangas, hasNextPage)
        }

        val path = filters.firstInstance<CategoryFilter>().value

        if (path == RANKING_PATH) {
            return getPopularManga(page)
        }

        return client.get("$baseUrl$path/$page").toMangasPage()
    }

    protected open fun Response.toMangasPage(): MangasPage {
        val document = this.asJsoup()
        val mangas = document.select("div.series-list-item").map {
            SManga.create().apply {
                url = it.selectFirst("a.series-list-item-link")!!.absUrl("href").toHttpUrl().seriesHash()
                title = it.selectFirst("div.series-list-item-h span[data-e2e=sliTitle]")!!.text()
                thumbnail_url = it.selectFirst("img.series-list-item-img")?.absUrl("src")
            }
        }

        val hasNextPage = document.selectFirst("a.g-pager-link.mode-active + a.g-pager-link") != null
        return MangasPage(mangas, hasNextPage)
    }

    /** Series links come as `/series/<hash>`, `/series/<hash>/new` or `/<magazine>/series/<hash>`. */
    protected open fun HttpUrl.seriesHash(): String {
        val index = pathSegments.indexOf("series")
        require(index != -1) { "Unrecognized series url: $this" }
        return pathSegments[index + 1]
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val hash = manga.url.substringAfter("/series/") // for old url compatibility
        val seriesAsync = async {
            client.get(seriesApiUrl("episodes", hash), CacheControl.FORCE_NETWORK)
                .parseAs<ApiResponse>().series
        }
        val accessAsync = async {
            client.get(seriesApiUrl("series/access", hash), CacheControl.FORCE_NETWORK)
                .parseAs<AccessApiResponse>().seriesAccess.episodeAccesses.associateBy { it.episodeId }
        }

        val series = seriesAsync.await()
        val accesses = accessAsync.await()
        val showLocked = preferences.getBoolean(SHOW_LOCKED_PREF_KEY, true)
        val showCampaignLocked = preferences.getBoolean(SHOW_CAMPAIGN_LOCKED_PREF_KEY, true)
        val chapterList = series.episodes.mapNotNull {
            val access = accesses[it.id]
            val isVisible = when {
                access == null || !access.isLocked -> true
                access.needsLogin -> showCampaignLocked
                else -> showLocked
            }

            it.toSChapter(access).takeIf { isVisible }
        }.reversed()

        SMangaUpdate(
            series.summary.toSManga(),
            chapterList,
        )
    }

    protected open fun seriesApiUrl(path: String, seriesHash: String) = "$apiUrl/$path".toHttpUrl().newBuilder()
        .addQueryParameter("seriesHash", seriesHash)
        .addQueryParameter("episodeFrom", "1")
        .addQueryParameter("episodeTo", "9999")
        .build()

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/episodes/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.memo["login"]?.boolean == true) {
            throw Exception("This chapter is free but you need to log in via WebView and refresh the entry.")
        }

        val episode = client.get("$apiUrl/episodes/${chapter.url}", CacheControl.FORCE_NETWORK).parseAs<EpisodeDetailsApiResponse>().episode
        val viewerId = episode.viewerId
            ?: return episode.content
                .mapNotNull { if (it.type == "image") it.url else null }
                .mapIndexed { index, url -> Page(index, imageUrl = url) }
                .ifEmpty { throw Exception("Log in via WebView and purchase this chapter to read.") }

        val memberJwt = try {
            client.get("$apiUrl/user/info", CacheControl.FORCE_NETWORK).parseAs<UserInfoApiResponse>().user?.id
        } catch (_: Exception) {
            null
        }

        val contentsInfoUrl = "$apiUrl/book/contentsInfo".toHttpUrl().newBuilder()
            .addQueryParameter("comici-viewer-id", viewerId)
            .addQueryParameter("contentId", episode.contentId.toString())
            .addQueryParameter("user-id", memberJwt)
            .addQueryParameter("page-from", "0")

        val totalPages = client.get(
            contentsInfoUrl.addQueryParameter("page-to", "0").build(),
            CacheControl.FORCE_NETWORK,
        ).parseAs<ViewerResponse>().totalPages

        val result = client.get(
            contentsInfoUrl.setQueryParameter("page-to", totalPages.toString()).build(),
            CacheControl.FORCE_NETWORK,
        ).parseAs<ViewerResponse>().result

        return result.map {
            Page(it.sort, imageUrl = "${it.imageUrl}#scramble=${it.scramble}")
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_LOCKED_PREF_KEY
            title = "Show Locked Chapters"
            setDefaultValue(true)
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_CAMPAIGN_LOCKED_PREF_KEY
            title = "Show 'Require Login' Chapters"
            summary = "Shows chapters that are free but require login."
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    protected open val extraFilterOptions: List<Pair<String, String>> = emptyList()

    protected open fun getFilterOptions(): List<Pair<String, String>> = listOf(
        "ランキング" to RANKING_PATH,
        "更新順" to "/series/list/up",
        "新作順" to "/series/list/new",
        "読み切り" to "/category/manga/oneShot",
        "完結" to "/category/manga/complete",
        "月曜日" to "/category/manga/day/1",
        "火曜日" to "/category/manga/day/2",
        "水曜日" to "/category/manga/day/3",
        "木曜日" to "/category/manga/day/4",
        "金曜日" to "/category/manga/day/5",
        "土曜日" to "/category/manga/day/6",
        "日曜日" to "/category/manga/day/7",
        "その他" to "/category/manga/day/8",
    ) + extraFilterOptions

    override fun getFilterList(data: JsonElement?) = FilterList(
        CategoryFilter(getFilterOptions()),
    )

    companion object {
        private const val RANKING_PATH = "/ranking/manga"
        private const val SEARCH_PAGE_SIZE = 24
        private const val SHOW_LOCKED_PREF_KEY = "pref_show_locked_chapters"
        private const val SHOW_CAMPAIGN_LOCKED_PREF_KEY = "pref_show_campaign_locked_chapters"
    }
}
