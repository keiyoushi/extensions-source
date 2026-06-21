package eu.kanade.tachiyomi.extension.ja.pixivcomic

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.publus.PublusContent
import keiyoushi.lib.publus.PublusInterceptor
import keiyoushi.lib.publus.fetchPages
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

@Source
abstract class PixivComic : HttpSource() {
    override val supportsLatest = true

    private val apiUrl = "$baseUrl/api/app"
    private val viewerUrl = "https://comic-store-viewer.$domain/api"
    private val preferences by getPreferencesLazy()
    private val pageSize = 30

    // since there's no page option for popular manga, we use this as storage storing manga id
    private val alreadyLoadedPopularMangaIds = mutableSetOf<Int>()

    private var serialSearchHasMore = true
    private var storeSearchHasMore = true
    private var categoryList: List<String> = emptyList()

    override val client = network.client.newBuilder()
        .addInterceptor(ImageInterceptor())
        .addInterceptor(PublusInterceptor())
        .addInterceptor {
            val request = it.request()
            val response = it.proceed(request)
            if (response.code == 400 && request.url.pathSegments.last() == "master") {
                throw IOException("Log in via WebView and purchase this volume to read it, even if it's free.")
            }
            response
        }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("X-Requested-With", "pixivcomic")

    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page == 1) alreadyLoadedPopularMangaIds.clear()
        val count = pageSize * page
        val url = "$apiUrl/rankings/popularity".toHttpUrl().newBuilder()
            .addQueryParameter("label", "総合")
            .addQueryParameter("count", count.toString())
            .build()

        val ranking = client.newCall(GET(url, headers)).awaitSuccess().parseAs<ApiResponse<PopularResponse>>().data.ranking
        val mangas = ranking
            .filter { alreadyLoadedPopularMangaIds.add(it.id) }
            .map { it.toSManga() }

        return MangasPage(mangas, mangas.isNotEmpty() && ranking.size >= count)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/works/recent_updates/v2".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        return client.newCall(GET(url, headers)).awaitSuccess().toMangasPage()
    }

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = coroutineScope {
        if (query.isBlank()) {
            val tag = filters.firstInstanceOrNull<TagsFilter>()?.state?.trim()?.removePrefix("#")
            val category = filters.firstInstanceOrNull<CategoryFilter>()?.selected
            val url = if (!tag.isNullOrEmpty()) {
                "$apiUrl/tags".toHttpUrl().newBuilder()
                    .addPathSegment(tag)
                    .addPathSegments("works/v2")
                    .addQueryParameter("page", page.toString())
                    .build()
            } else {
                "$apiUrl/categories".toHttpUrl().newBuilder()
                    .addPathSegment(category.orEmpty())
                    .addPathSegment("works")
                    .addQueryParameter("page", page.toString())
                    .build()
            }
            return@coroutineScope client.newCall(GET(url, headers)).awaitSuccess().toMangasPage()
        }

        if (page == 1) {
            serialSearchHasMore = true
            storeSearchHasMore = true
        }

        val serial = if (serialSearchHasMore) {
            async {
                val url = "$apiUrl/works/search/v2".toHttpUrl().newBuilder()
                    .addPathSegment(query)
                    .addQueryParameter("page", page.toString())
                    .build()
                client.newCall(GET(url, headers)).awaitSuccess().parseAs<ApiResponse<SeriesResponse>>().data
            }
        } else {
            null
        }
        val store = if (storeSearchHasMore) {
            async {
                val url = "$apiUrl/store/search/v2".toHttpUrl().newBuilder()
                    .addPathSegment(query)
                    .addQueryParameter("page", page.toString())
                    .build()
                client.newCall(GET(url, headers)).awaitSuccess().parseAs<ApiResponse<StoreSearchResponse>>().data
            }
        } else {
            null
        }

        val serialResult = serial?.await()
        val storeResult = store?.await()
        serialSearchHasMore = serialResult?.hasNextPage() ?: false
        storeSearchHasMore = storeResult?.hasNextPage() ?: false

        val mangas = serialResult?.officialWorks.orEmpty().map { it.toSManga() } + storeResult?.products.orEmpty().map { it.toSManga() }
        MangasPage(mangas, serialSearchHasMore || storeSearchHasMore)
    }

    private fun Response.toMangasPage(): MangasPage {
        val result = parseAs<ApiResponse<SeriesResponse>>()
        val mangas = result.data.officialWorks.map { it.toSManga() }
        return MangasPage(mangas, result.data.hasNextPage())
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Search query replaces the filters below!"),
        Filter.Header("Only one filter can be used at a time!"),
        TagsFilter(),
        if (categoryList.isEmpty()) {
            Filter.Header("Press 'Reset' to load categories")
        } else {
            CategoryFilter(categoryList)
        },
    ).apply { fetchCategoryList() }

    private fun fetchCategoryList() {
        if (categoryList.isNotEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                client.newCall(GET("$apiUrl/categories", headers)).awaitSuccess()
                    .parseAs<ApiResponse<CategoryResponse>>()
                    .data.categories.map { it.name }
            }.onSuccess { categoryList = it }
        }
    }

    override fun getMangaUrl(manga: SManga): String {
        val url = "$baseUrl/${manga.url}".toHttpUrl().pathSegments
        return if (url.first() == "store") {
            "$baseUrl/store/products/${url.last()}"
        } else {
            "$baseUrl/works/${manga.url}"
        }
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val url = "$baseUrl/${manga.url}".toHttpUrl().pathSegments
        if (url.first() == "store") {
            val product = fetchProduct(url.last())
            val updatedManga = if (fetchDetails) product.product.toSManga() else manga
            val updatedChapters = if (fetchChapters) {
                product.variants
                    .filter { !hideLocked || !it.isLocked }
                    .map { it.toSChapter() }
            } else {
                chapters
            }
            return@coroutineScope SMangaUpdate(updatedManga, updatedChapters)
        }

        val officialWork = if (fetchDetails || fetchChapters) {
            async { fetchOfficialWork(manga.url) }
        } else {
            null
        }
        val episodes = if (fetchChapters) {
            async { fetchEpisodes(manga.url) }
        } else {
            null
        }

        val officialWorkResult = officialWork?.await()
        val updatedManga = if (fetchDetails) officialWorkResult!!.toSManga() else manga

        val updatedChapters = if (fetchChapters) {
            val storeProductKey = officialWorkResult?.storeProductKey
            val volumeChapters = storeProductKey?.takeIf(String::isNotEmpty)?.let { fetchVolumes(it) }.orEmpty()
                .filter { !hideLocked || !it.isLocked }
                .map { it.toSChapter() }
            val episodeChapters = episodes!!.await()
                .filter { !hideLocked || !it.isLocked }
                .map { it.toSChapter() }
            episodeChapters + volumeChapters
        } else {
            chapters
        }

        SMangaUpdate(updatedManga, updatedChapters)
    }

    private suspend fun fetchOfficialWork(mangaId: String): OfficialWork = client.newCall(GET("$apiUrl/works/v5/$mangaId", headers)).awaitSuccess().parseAs<ApiResponse<DetailsResponse>>().data.officialWork

    private suspend fun fetchEpisodes(mangaId: String): List<Episode> {
        val url = "$apiUrl/works/$mangaId/episodes/v2".toHttpUrl().newBuilder()
            .addQueryParameter("order", "desc")
            .build()
        return client.newCall(GET(url, headers)).awaitSuccess()
            .parseAs<ApiResponse<ChapterResponse>>()
            .data.episodes
            .mapNotNull { it.episode }
    }

    private suspend fun fetchVolumes(storeProductKey: String): List<Variant> {
        val url = "$apiUrl/store/products/$storeProductKey/variants/v3".toHttpUrl().newBuilder()
            .addQueryParameter("order", "desc")
            .addQueryParameter("view_type", "product_detail")
            .build()
        return client.newCall(GET(url, headers)).awaitSuccess()
            .parseAs<ApiResponse<VolumeResponse>>()
            .data.variants
    }

    private suspend fun fetchProduct(storeProductKey: String): ProductResponse = client.newCall(GET("$apiUrl/store/products/v2/$storeProductKey", headers)).awaitSuccess().parseAs<ApiResponse<ProductResponse>>().data

    override fun getChapterUrl(chapter: SChapter): String = if (chapter.url.any { it.isLetter() }) {
        "$baseUrl/store/viewers/${chapter.url}/master"
    } else {
        "$baseUrl/viewer/stories/${chapter.url}"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterResponse = client.newCall(GET(getChapterUrl(chapter), headers)).awaitSuccess()
        if (chapter.url.any { it.isLetter() }) {
            val authUrl = generateSequence(chapterResponse) { it.priorResponse }
                .map { it.request.url }
                .firstOrNull { it.queryParameter("u1") != null || it.queryParameter("u2") != null }

            val cid = authUrl?.queryParameter("cid")
            val u1 = authUrl?.queryParameter("u1")
            val u2 = authUrl?.queryParameter("u2")
            val cUrl = "$viewerUrl/c".toHttpUrl().newBuilder()
                .addQueryParameter("cid", cid)
                .apply {
                    if (u1 != null) {
                        addQueryParameter("u1", u1)
                    }
                    if (u2 != null) {
                        addQueryParameter("u2", u2)
                    }
                }.build()

            val content = client.newCall(GET(cUrl, headers)).execute().parseAs<PublusContent>()

            if (content.url.isNullOrEmpty()) {
                throw Exception("Log in via WebView and purchase this volume to read it, even if it's free.")
            }

            return fetchPages(content.url!!, headers, client, content.authInfo?.toAuth(), hashFilenames = false)
        }

        val salt = chapterResponse.asJsoup().extractNextJs<SaltResponse>()?.props?.pageProps?.salt.orEmpty()
        val (time, hash) = getTimeAndHash(salt)
        val header = headersBuilder()
            .add("X-Client-Time", time)
            .add("X-Client-Hash", hash)
            .build()
        val pages = client.newCall(GET("$apiUrl/episodes/${chapter.url}/read_v4", header)).awaitSuccess()
            .parseAs<ApiResponse<ViewerResponse>>()
            .data.readingEpisode.pages
        if (pages.isNullOrEmpty()) {
            throw Exception("Log in via WebView and purchase this chapter to read.")
        }

        return pages.mapIndexed { i, page ->
            Page(i, imageUrl = "${page.url}#key=${page.key}")
        }
    }

    override fun imageRequest(page: Page): Request {
        val pageUrl = page.imageUrl!!.toHttpUrl()
        if (pageUrl.host.contains("publus") || !pageUrl.fragment!!.startsWith("key=")) {
            return super.imageRequest(page)
        }

        val key = pageUrl.fragment!!.substringAfter("key=")
        val newHeaders = headersBuilder()
            .add("X-Cobalt-Thumber-Parameter-GridShuffle-Key", key)
            .build()
        return GET(page.imageUrl!!, newHeaders)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
