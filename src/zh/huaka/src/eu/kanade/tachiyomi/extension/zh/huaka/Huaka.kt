package eu.kanade.tachiyomi.extension.zh.huaka

import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.applicationContext
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJsRsc
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Element
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Instant

/**
 * Huaka / 花咔漫画 source for Mihon.
 *
 * Discovery search and filters use the public comic-search JSON endpoint.
 * Manga/chapter detail is extracted from the official Next.js RSC payload.
 * Chapter images use the public comic-pages JSON cursor API.
 */
@Source
abstract class Huaka :
    KeiSource(),
    ConfigurableSource {
    private val preferences = getPreferences()
    private val comicSearchPageTokens = ConcurrentHashMap<String, ConcurrentHashMap<Int, String>>()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context)
            .apply {
                key = PREF_LOGIN_ACTION
                title = "打开花咔官方登录页面"
                summary = "通过 Mihon 内置 WebView 登录 Picacomic ID；完成后返回这里检查状态"
                setOnPreferenceClickListener {
                    val context = applicationContext
                    val loginUrl = "$baseUrl/zh-CN/login?redirect=%2Fzh-CN%2Fannouncements"
                    val intent =
                        Intent().apply {
                            component = ComponentName(context, "eu.kanade.tachiyomi.ui.webview.WebViewActivity")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            putExtra("url_key", loginUrl)
                            putExtra("source_key", id)
                            putExtra("title_key", "花咔 Picacomic ID 登录")
                        }
                    val opened = runCatching { context.startActivity(intent) }
                    if (opened.isFailure) {
                        summary = "无法打开内置 WebView。请从花咔图源的菜单中选择「在 WebView 中打开」"
                    }
                    true
                }
            }.let(screen::addPreference)

        EditTextPreference(screen.context)
            .apply {
                key = PREF_CHECK_LOGIN
                title = "检查登录状态"
                summary = "向花咔用户资料接口确认 Cookie 是否有效"
                setOnPreferenceClickListener {
                    summary = "正在检查花咔登录状态……"
                    Thread {
                        val result =
                            runCatching {
                                kotlinx.coroutines.runBlocking {
                                    val url = "$baseUrl/api/bff/users/me?profileRefresh=1"
                                    client.get(url, Fetch.XHR.from("$baseUrl/zh-CN/announcements")).use { response ->
                                        when (response.code) {
                                            200 -> "已登录：花咔服务器已确认当前会话有效"
                                            401 -> "未登录或会话失效：请使用上方官方登录页重新登录"
                                            else -> "登录状态暂时无法确认：服务器返回 HTTP ${response.code}"
                                        }
                                    }
                                }
                            }.getOrElse { error ->
                                "检查失败：${error.message?.take(120) ?: "网络请求失败"}"
                            }
                        Handler(Looper.getMainLooper()).post { summary = result }
                    }.start()
                    true
                }
            }.let(screen::addPreference)

        EditTextPreference(screen.context)
            .apply {
                key = PREF_CLEAR_PASSWORD
                title = "清除旧版保存的密码"
                summary = "清除扩展普通偏好设置中旧版保存的密码；不影响网页登录 Cookie"
                setOnPreferenceClickListener {
                    preferences.edit().remove(PREF_PASSWORD).apply()
                    summary = "旧版保存的密码已清除"
                    true
                }
            }.let(screen::addPreference)
    }

    override val supportsLatest = true

    private val rscHeaders: Headers
        get() = headersBuilder().set("Rsc", "1").build()

    override fun getHomeUrl(): String = "$baseUrl/zh-CN"

    // Huaka accepts category names (not category IDs) in comic-search.
    // Sorting and category selection are applied server-side, before pagination.
    // Keep the filter sheet compact: each major area is a native collapsible group.
    // Mihon itself provides the sticky Reset / Filter actions at the top of the sheet.
    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        HuakaBasicFilterGroup(),
        Filter.Separator(),
        HuakaCategoryGroup(),
        Filter.Separator(),
        HuakaTagFilterGroup(),
        Filter.Separator(),
        HuakaOtherFilterGroup(),
    )

    // The official web pages for these two feeds are verified. Keep using
    // their HTML/RSC-backed card list instead of sending an unverified empty
    // query to comic-search (which can return HTTP 400).
    override suspend fun getPopularManga(page: Int): MangasPage = loadPublicListing(page, "recommendations")

    override suspend fun getLatestUpdates(page: Int): MangasPage = loadPublicListing(page, "latest")

    // The discovery endpoint returns opaque cursors scoped to the full search criteria.
    private suspend fun loadComicSearchPage(
        page: Int,
        criteria: HuakaSearchCriteria,
    ): MangasPage {
        require(page >= 1) { "Invalid Huaka page: $page" }

        val tokens = comicSearchPageTokens.computeIfAbsent(criteria.cacheKey) { ConcurrentHashMap() }
        if (page == 1) tokens.clear()

        var pageToken = if (page == 1) null else tokens[page]
        if (page > 1 && pageToken == null) {
            var cursor: String? = null
            for (previousPage in 1 until page) {
                val previousData = requestComicSearchPage(criteria, cursor)
                cursor = previousData.nextPageToken?.takeIf(String::isNotBlank)
                    ?: return MangasPage(emptyList(), false)
                tokens[previousPage + 1] = cursor
            }
            pageToken = cursor
        }

        val data = requestComicSearchPage(criteria, pageToken)
        val nextToken = data.nextPageToken?.takeIf(String::isNotBlank)
        if (nextToken != null) {
            check(nextToken != pageToken) { "Huaka returned a duplicate comic-search pageToken" }
            tokens[page + 1] = nextToken
        } else {
            tokens.remove(page + 1)
        }
        return MangasPage(data.comics.map { it.toManga() }, nextToken != null)
    }

    private suspend fun requestComicSearchPage(
        criteria: HuakaSearchCriteria,
        pageToken: String?,
    ): ComicSearchData {
        val builder =
            "$baseUrl/api/bff/comic-search"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("pageSize", HUAKA_PAGE_SIZE.toString())
                .addQueryParameter("sort", criteria.sort)
                .addHuakaFilters(criteria)

        pageToken?.let { builder.addQueryParameter("pageToken", it) }
        val referer = buildSearchReferer(criteria)
        val response =
            client
                .get(builder.build(), Fetch.XHR.from(referer))
                .parseAs<ComicSearchResponse>()

        check(response.statusCode == 200) {
            "花咔漫画列表请求失败：HTTP ${response.statusCode} ${response.message}"
        }
        return response.data ?: error("花咔漫画列表没有返回 data")
    }

    private fun HttpUrl.Builder.addHuakaFilters(criteria: HuakaSearchCriteria): HttpUrl.Builder = apply {
        if (criteria.query.isNotBlank()) addQueryParameter("query", criteria.query)
        criteria.categories.forEach { addQueryParameter("categories", it) }
        criteria.tags.forEach { addQueryParameter("tags", it) }
        criteria.languages.forEach { addQueryParameter("languages", it) }
        criteria.translationTeams.forEach { addQueryParameter("translationTeams", it) }
        criteria.finished?.let { addQueryParameter("finished", it.toString()) }
    }

    private fun buildSearchReferer(criteria: HuakaSearchCriteria): String {
        val builder = "$baseUrl/zh-CN".toHttpUrl().newBuilder()
        when {
            criteria.query.isNotBlank() || criteria.categories.size > 1 -> {
                builder.addPathSegment("search")
            }
            criteria.categories.size == 1 -> {
                builder.addPathSegment("categories").addPathSegment(criteria.categories.single())
            }
        }
        return builder
            .addQueryParameter("sort", criteria.sort)
            .addHuakaFilters(criteria)
            .build()
            .toString()
    }

    private suspend fun loadPublicListing(
        page: Int,
        route: String,
    ): MangasPage {
        // The verified website routes expose the first batch as server-rendered
        // comic links. Do not claim cursor pagination until its home-feed token
        // protocol has been captured separately.
        if (page != 1) return MangasPage(emptyList(), false)

        val url = "$baseUrl/zh-CN/$route"
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val seen = mutableSetOf<String>()
        val mangas =
            document.select("a[href*='/comics/']").mapNotNull { link ->
                val parsed =
                    runCatching { link.absUrl("href").toHttpUrl() }.getOrNull()
                        ?: return@mapNotNull null
                if (parsed.host != baseUrl.toHttpUrl().host) return@mapNotNull null

                val segments = parsed.pathSegments
                val index = segments.indexOf("comics")
                val code = segments.getOrNull(index + 1).orEmpty()
                if (index < 0 || code.isBlank() || code in seen) return@mapNotNull null

                val title =
                    listOf(
                        link.selectFirst("h2, h3, h4, h5")?.text(),
                        link.selectFirst("img[alt]")?.attr("alt"),
                        link.attr("title"),
                        link.selectFirst("[title]")?.attr("title"),
                        link.text(),
                    ).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
                if (title.isBlank()) return@mapNotNull null

                val cover = findCardCoverUrl(link)
                seen.add(code)
                SManga.create().apply {
                    this.url = "/zh-CN/comics/$code"
                    this.title = title
                    thumbnail_url = cover
                    genre = link.text()
                }
            }
        check(mangas.isNotEmpty()) {
            "花咔 $route 列表没有解析出漫画卡片，请检查网站页面结构"
        }

        // Some cards contain the title/link but not the lazy-loaded cover.
        // Resolve only missing covers with bounded concurrency.
        val withoutCover = mangas.filter { it.thumbnail_url.isNullOrBlank() }
        if (withoutCover.isNotEmpty()) {
            val limiter = Semaphore(4)
            coroutineScope {
                withoutCover
                    .map { manga ->
                        async {
                            limiter.withPermit {
                                runCatching {
                                    val body = fetchMangaRsc(manga)
                                    val data =
                                        body.extractNextJsRsc<CoverData> { element ->
                                            element is JsonObject &&
                                                "title" in element && "thumbnailUrl" in element
                                        }
                                    val image = data?.thumbnailUrl?.let(::normalizedCoverUrl)
                                    if (image != null) manga.thumbnail_url = image
                                }
                            }
                        }
                    }.awaitAll()
            }
        }
        return MangasPage(mangas, false)
    }

    private fun findCardCoverUrl(link: Element): String? {
        var container: Element? = link
        repeat(4) {
            if (container == null) return@repeat
            val node = container!!
            if (node.select("a[href*='/comics/']").size <= 1) {
                node.select("img").forEach { img ->
                    val candidates =
                        listOf(
                            img.attr("data-src"),
                            img.attr("data-original"),
                            img.attr("data-lazy-src"),
                            img.attr("src"),
                            img.attr("data-srcset").substringBefore(',').substringBeforeLast(' '),
                            img.attr("srcset").substringBefore(',').substringBeforeLast(' '),
                        )
                    candidates.forEach { candidate ->
                        normalizedCoverUrl(candidate)?.let { return it }
                    }
                }
            }
            container = node.parent()
        }
        return null
    }

    private fun normalizedCoverUrl(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty() || value.startsWith("data:") || value.startsWith("blob:")) return null
        val result = runCatching { baseUrl.toHttpUrl().resolve(value) }.getOrNull() ?: return null
        if (result.scheme != "https") return null
        val path = result.encodedPath.lowercase()
        if (path.endsWith(".svg") || path.contains("placeholder") ||
            path.contains("skeleton") || path.contains("fallback")
        ) {
            return null
        }
        return result.toString()
    }

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        // Filters are nested inside native collapsible groups, so read all descendants.
        val allFilters = filters.flatMap { it.flattenFilters() }
        val selectedCategories =
            allFilters
                .filterIsInstance<HuakaCategoryGroup>()
                .firstOrNull()
                ?.state
                .orEmpty()
                .filter { it.state }
                .map { it.value }
                .distinct()
        val sortIndex = allFilters.filterIsInstance<HuakaSortFilter>().firstOrNull()?.state ?: 0

        val selectedTags =
            allFilters
                .filterIsInstance<HuakaTagFilter>()
                .filter { it.state }
                .map { it.value }
        val customTags =
            allFilters
                .filterIsInstance<HuakaCustomTagsFilter>()
                .firstOrNull()
                ?.state
                ?.let(::splitHuakaMultiValues)
                .orEmpty()
        val tags = (selectedTags + customTags).distinct()
        require(tags.size <= 10) { "花咔标签最多选择 10 个，目前选中了 ${tags.size} 个" }

        val languages =
            allFilters
                .filterIsInstance<HuakaLanguageFilter>()
                .filter { it.state }
                .map { it.code }
        require(languages.size <= 5) { "花咔语言最多选择 5 个" }

        val finishedIndex = allFilters.filterIsInstance<HuakaFinishedFilter>().firstOrNull()?.state ?: 0
        val finished =
            when (finishedIndex) {
                1 -> false
                2 -> true
                else -> null
            }
        val translationTeams =
            allFilters
                .filterIsInstance<HuakaTranslationTeamsFilter>()
                .firstOrNull()
                ?.state
                ?.let(::splitHuakaMultiValues)
                .orEmpty()

        val criteria =
            HuakaSearchCriteria(
                query = query.trim(),
                sort = HUAKA_SORT_OPTIONS.getOrNull(sortIndex)?.second ?: "latest",
                categories = selectedCategories,
                tags = tags,
                languages = languages,
                finished = finished,
                translationTeams = translationTeams,
            )
        require(
            criteria.query.isNotBlank() || criteria.categories.isNotEmpty() ||
                criteria.tags.isNotEmpty() || criteria.languages.isNotEmpty() ||
                criteria.translationTeams.isNotEmpty() || criteria.finished != null,
        ) { "请先输入关键词或选择一个筛选条件" }
        return loadComicSearchPage(page, criteria)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        val index = segments.indexOf("comics")
        if (index < 0) return null
        val slug = segments.getOrNull(index + 1)?.takeIf(String::isNotBlank) ?: return null
        val manga =
            SManga.create().apply {
                this.url = "/zh-CN/comics/$slug"
                title = slug
            }
        return runCatching {
            parseMangaMetadata(manga, fetchMangaRsc(manga))
            manga
        }.getOrNull()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val body = fetchMangaRsc(manga)
        parseMangaMetadata(manga, body)

        val episodeData =
            body.extractNextJsRsc<EpisodeData> { element ->
                element is JsonObject && "initialEpisodes" in element && "comicId" in element
            } ?: error("Could not parse Huaka chapter list")

        // The detail RSC contains the first batch and an opaque cursor.
        // Continue through the official episodes endpoint until every chapter is collected.
        val allEpisodes = episodeData.initialEpisodes.distinctBy { it.id }.toMutableList()
        val seenEpisodeIds = allEpisodes.mapTo(mutableSetOf()) { it.id }
        val seenPageTokens = mutableSetOf<String>()
        var expectedTotalCount = episodeData.initialTotalCount
        var nextPageToken = episodeData.initialNextPageToken?.takeIf(String::isNotBlank)
        val referer = getMangaUrl(manga)

        while (nextPageToken != null) {
            check(seenPageTokens.add(nextPageToken)) {
                "Huaka episodes returned a duplicate nextPageToken"
            }

            val requestUrl =
                "$baseUrl/api/bff/episodes"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("comicId", episodeData.comicId)
                    .addQueryParameter("sortOrder", "desc")
                    .addQueryParameter("pageSize", "25")
                    .addQueryParameter("pageToken", nextPageToken)
                    .build()

            val response =
                client
                    .get(requestUrl, Fetch.XHR.from(referer))
                    .parseAs<EpisodesResponse>()

            check(response.statusCode == 200) {
                "Huaka chapter request failed: HTTP ${response.statusCode} ${response.message}"
            }
            val data = response.data ?: error("Huaka chapter request returned no data")
            data.totalCount?.let { apiTotalCount ->
                check(expectedTotalCount == null || expectedTotalCount == apiTotalCount) {
                    "Huaka chapter total count mismatch: RSC reported $expectedTotalCount, API reported $apiTotalCount"
                }
                expectedTotalCount = apiTotalCount
            }
            data.episodes.forEach { episode ->
                if (seenEpisodeIds.add(episode.id)) allEpisodes += episode
            }

            nextPageToken = data.nextPageToken?.takeIf(String::isNotBlank)
            require(seenPageTokens.size < 100) {
                "Huaka chapter pagination exceeded 100 batches"
            }
        }

        expectedTotalCount?.let { total ->
            check(allEpisodes.size == total) {
                "Huaka chapter list is incomplete: loaded ${allEpisodes.size} of $total chapters"
            }
        }

        val chapterList =
            allEpisodes.sortedByDescending { it.order }.map { entry ->
                SChapter.create().apply {
                    url = "${manga.url.trimEnd('/')}/episodes/${entry.id}"
                    name = entry.title
                    chapter_number = entry.order.toFloat()
                    date_upload = Instant.tryParse(entry.createdAt)
                }
            }
        return SMangaUpdate(manga, chapterList)
    }

    private suspend fun fetchMangaRsc(manga: SManga): String {
        val referer = getMangaUrl(manga)
        val url =
            referer
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("_rsc", "")
                .build()
        return client.get(url, rscHeaders, Fetch.XHR.from(referer)).use { response ->
            check(response.isSuccessful) { "Comic detail request failed: HTTP ${response.code}" }
            response.body.string()
        }
    }

    private fun parseMangaMetadata(
        manga: SManga,
        body: String,
    ) {
        val cover =
            body.extractNextJsRsc<CoverData> { element ->
                element is JsonObject && "title" in element && "thumbnailUrl" in element
            }
        cover?.let {
            manga.title = it.title
            it.thumbnailUrl?.let(::normalizedCoverUrl)?.let { image -> manga.thumbnail_url = image }
        }

        val identity =
            body.extractNextJsRsc<IdentityData> { element ->
                element is JsonObject && "comic" in element && "visibility" in element
            }
        identity?.comic?.let {
            if (it.authors.isNotEmpty()) manga.author = it.authors.joinToString()
            if (it.tags.isNotEmpty()) manga.genre = it.tags.joinToString()
        }

        body
            .extractNextJsRsc<DescriptionData> { element ->
                element is JsonObject && "text" in element && "collapsedClassName" in element
            }?.text
            ?.let { manga.description = it }
    }

    override fun getImageRequest(page: Page): Request = super
        .getImageRequest(page)
        .newBuilder()
        .fetch(imageFetch.from(page.url))
        .build()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val episodeId = chapter.url.substringAfterLast('/').substringBefore('?')
        require(episodeId.matches(Regex("[a-fA-F0-9]{24}"))) { "Invalid episodeId" }

        val imageUrls = mutableListOf<String>()
        val seenTokens = mutableSetOf<String>()
        var nextPageToken: String? = null
        do {
            val builder =
                "$baseUrl/api/bff/comic-pages"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("episodeId", episodeId)
                    .addQueryParameter("pageSize", "25")
                    .addQueryParameter("mediaSize", "read")
            nextPageToken?.let { builder.addQueryParameter("pageToken", it) }

            val response =
                client
                    .get(
                        builder.build(),
                        Fetch.XHR.from(getChapterUrl(chapter)),
                    ).parseAs<ComicPagesResponse>()
            require(response.statusCode == 200) {
                "comic-pages failed: ${response.statusCode} ${response.message}"
            }
            val data = response.data ?: error("comic-pages returned no data: ${response.message}")
            imageUrls += data.pages.map { it.mediaUrl }

            nextPageToken = data.nextPageToken?.takeIf(String::isNotBlank)
            if (nextPageToken != null && !seenTokens.add(nextPageToken)) {
                error("comic-pages returned a duplicate nextPageToken")
            }
            require(seenTokens.size < 100) { "comic-pages pagination exceeded 100 batches" }
        } while (nextPageToken != null)

        require(imageUrls.isNotEmpty()) { "comic-pages returned zero images" }
        val chapterUrl = getChapterUrl(chapter)
        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, url = chapterUrl, imageUrl = imageUrl)
        }
    }
}

@Serializable
private data class ComicSearchResponse(
    val statusCode: Int,
    val message: String = "",
    val data: ComicSearchData? = null,
)

@Serializable
private data class ComicSearchData(
    val comics: List<ComicEntry> = emptyList(),
    val nextPageToken: String? = null,
    val totalCount: Int? = null,
    val facets: JsonElement? = null,
)

@Serializable
private data class ComicEntry(
    val id: String,
    val title: String,
    val huaCode: String? = null,
    val thumbnailUrl: String? = null,
    val description: String? = null,
    val authors: List<String> = emptyList(),
    val translationTeams: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val finished: Boolean? = null,
    val totalEpisodes: Int? = null,
    val totalPages: Int? = null,
    val totalReads: Long? = null,
    val totalBookmarks: Long? = null,
    val totalLikes: Long? = null,
    val updatedAt: String? = null,
    val createdAt: String? = null,
) {
    fun toManga(): SManga = SManga.create().apply {
        url = "/zh-CN/comics/${huaCode?.takeIf(String::isNotBlank) ?: id}"
        title = this@ComicEntry.title
        thumbnail_url = thumbnailUrl
        description = this@ComicEntry.description
        author = authors.joinToString()
        genre = (categories + tags).distinct().joinToString()
        status =
            when (finished) {
                true -> SManga.COMPLETED
                false -> SManga.ONGOING
                null -> SManga.UNKNOWN
            }
    }
}

@Serializable
private data class EpisodeData(
    val comicId: String,
    val initialEpisodes: List<EpisodeEntry>,
    val initialNextPageToken: String? = null,
    val initialTotalCount: Int? = null,
)

@Serializable
private data class EpisodesResponse(
    val statusCode: Int,
    val message: String = "",
    val data: EpisodesData? = null,
)

@Serializable
private data class EpisodesData(
    val episodes: List<EpisodeEntry> = emptyList(),
    val nextPageToken: String? = null,
    val totalCount: Int? = null,
)

@Serializable
private data class EpisodeEntry(
    val id: String,
    val title: String,
    val order: Int,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val pagesCount: Int? = null,
    val thumbnailUrl: String? = null,
    val comicId: String? = null,
)

@Serializable
private data class CoverData(
    val title: String,
    val thumbnailUrl: String? = null,
)

@Serializable
private data class DescriptionData(
    val text: String,
)

@Serializable
private data class IdentityData(
    val comic: IdentityComic,
)

@Serializable
private data class IdentityComic(
    val authors: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)

@Serializable
private data class ComicPagesResponse(
    val statusCode: Int,
    val message: String = "",
    val data: ComicPagesData? = null,
)

@Serializable
private data class ComicPagesData(
    val pages: List<ComicPage> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
private data class ComicPage(
    val mediaUrl: String,
)

private const val PREF_PASSWORD = "PASSWORD"
private const val PREF_LOGIN_ACTION = "LOGIN_ACTION"
private const val PREF_CHECK_LOGIN = "CHECK_LOGIN_ACTION"
private const val PREF_CLEAR_PASSWORD = "CLEAR_SAVED_PASSWORD_ACTION"

private const val HUAKA_PAGE_SIZE = 20

private class HuakaSearchCriteria(
    val query: String,
    val sort: String,
    val categories: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val finished: Boolean? = null,
    val translationTeams: List<String> = emptyList(),
) {
    val cacheKey: String =
        listOf(
            query,
            sort,
            categories.joinToString("\\u001e"),
            tags.joinToString("\\u001e"),
            languages.joinToString("\\u001e"),
            finished?.toString().orEmpty(),
            translationTeams.joinToString("\\u001e"),
        ).joinToString("\\u001f")
}

// Keep category labels and query values identical to the official website.
private val HUAKA_CATEGORY_OPTIONS =
    listOf(
        "短篇" to "短篇",
        "長篇" to "長篇",
        "全彩" to "全彩",
        "同人" to "同人",
        "正太" to "正太",
        "FURRY" to "FURRY",
        "純愛" to "純愛",
        "NTR" to "NTR",
        "韓漫｜WEBTOON" to "韓漫｜WEBTOON",
        "調教｜BDSM" to "調教｜BDSM",
        "CG雜圖" to "CG雜圖",
        "生肉" to "生肉",
        "一般向丨無H內容" to "一般向丨無H內容",
        "重口丨獵奇" to "重口丨獵奇",
        "ABO" to "ABO",
        "女體化" to "女體化",
        "互攻" to "互攻",
        "筋肉系" to "筋肉系",
        "單行本" to "單行本",
    )

private val HUAKA_SORT_OPTIONS =
    listOf(
        "最近更新" to "latest",
        "最多阅读" to "most_read",
        "最多收藏" to "most_bookmarked",
        "最多点赞" to "most_liked",
        "相关度" to "relevance",
    )

private class HuakaCategoryFilter(
    val value: String,
    name: String,
) : Filter.CheckBox(name)

private class HuakaCategoryGroup :
    Filter.Group<HuakaCategoryFilter>(
        "分类（多选）",
        HUAKA_CATEGORY_OPTIONS.map { (name, value) -> HuakaCategoryFilter(value, name) },
    )

private class HuakaSortFilter : Filter.Select<String>("排序", HUAKA_SORT_OPTIONS.map { it.first }.toTypedArray())

private class HuakaBasicFilterGroup :
    Filter.Group<Filter<*>>(
        "基础条件",
        listOf<Filter<*>>(
            HuakaSortFilter(),
            HuakaFinishedFilter(),
        ),
    )

private val HUAKA_TAG_OPTIONS =
    listOf(
        "肛交",
        "口交",
        "肛內中出",
        "正太",
        "純愛",
        "筋肉系",
        "獸耳",
        "女性向",
        "非人類",
        "偽娘",
        "furry",
        "獸人",
        "無修正",
        "無h內容",
        "全彩",
        "強暴",
        "橫切面",
        "偶像夢幻祭",
        "自慰",
        "原神",
    )

// Additional real tag values found in the official search-page facets and comic cards.
// The custom-tag field remains available for tags not listed here.
private val HUAKA_EXTRA_TAG_OPTIONS =
    listOf(
        "人外",
        "校園",
        "進擊的巨人",
        "進擊的巨人: 利威爾",
        "進擊的巨人: 艾倫",
        "3p",
        "sm",
        "制服",
        "劇情",
        "寸止",
        "年下",
        "性玩具",
        "懸疑",
        "戀愛喜劇",
        "獸交",
        "羣交",
        "男高中生(DK)",
        "辦公室",
        "職場",
        "深喉",
        "拘束",
        "第五人格",
        "推理",
        "斷肢",
        "重口 / 獵奇",
        "開腸剖肚",
        "轉生",
        "崩壞：星穹鐵道",
        "原神: 萊歐斯利",
        "原神: 那維萊特",
        "MOB (路人)",
        "二棒同穴",
        "異種奸",
        "幼馴染",
        "奇幻/魔幻",
        "手交",
    )

private class HuakaTagFilter(
    val value: String,
) : Filter.CheckBox(value)

private class HuakaCustomTagsFilter : Filter.Text("自定义标签（多个用逗号、分号或换行分隔）")

private class HuakaTagFilterGroup :
    Filter.Group<Filter<*>>(
        "标签（最多10个；多选任一匹配）",
        buildList<Filter<*>> {
            add(Filter.Header("常用标签"))
            HUAKA_TAG_OPTIONS.forEach { add(HuakaTagFilter(it)) }
            add(Filter.Separator())
            add(Filter.Header("更多标签"))
            HUAKA_EXTRA_TAG_OPTIONS.forEach { add(HuakaTagFilter(it)) }
            add(Filter.Separator())
            add(HuakaCustomTagsFilter())
        },
    )

private fun splitHuakaMultiValues(raw: String): List<String> = raw
    .split(Regex("[,，;；\\n]+"))
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()

private val HUAKA_LANGUAGE_OPTIONS =
    listOf(
        "简体中文" to "zh_cn",
        "日文" to "ja",
        "繁体中文" to "zh_tw",
        "英文" to "en",
        "韩文" to "ko",
        "其他" to "other",
    )

private class HuakaLanguageFilter(
    name: String,
    val code: String,
) : Filter.CheckBox(name)

private class HuakaFinishedFilter : Filter.Select<String>("连载状态", arrayOf("全部", "连载中", "已完结"))

private class HuakaTranslationTeamsFilter : Filter.Text("汉化组（多个名称用逗号、分号或换行分隔）")

private class HuakaOtherFilterGroup :
    Filter.Group<Filter<*>>(
        "其他条件",
        buildList<Filter<*>> {
            add(Filter.Header("语言（最多选择5个）"))
            HUAKA_LANGUAGE_OPTIONS.forEach { (name, code) ->
                add(HuakaLanguageFilter(name, code))
            }
            add(Filter.Separator())
            add(HuakaTranslationTeamsFilter())
        },
    )

private fun Filter<*>.flattenFilters(): List<Filter<*>> {
    val children =
        (this as? Filter.Group<*>)
            ?.state
            ?.filterIsInstance<Filter<*>>()
            .orEmpty()
    return listOf(this) + children.flatMap { it.flattenFilters() }
}
