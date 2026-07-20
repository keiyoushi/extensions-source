package eu.kanade.tachiyomi.extension.ja.pixivcomic

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.lib.publus.PublusContent
import keiyoushi.lib.publus.PublusInterceptor
import keiyoushi.lib.publus.fetchPages
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

@Source
abstract class PixivComic :
    KeiSource(),
    ConfigurableSource {
    private val apiUrl = "$baseUrl/api/app"
    private val viewerUrl = "https://comic-store-viewer.pixiv.net/api"
    private val preferences by getPreferencesLazy()
    private val pageSize = 30

    // since there's no page option for popular manga, we use this as storage storing manga id
    private val alreadyLoadedPopularMangaIds = mutableSetOf<Int>()

    private var serialSearchHasMore = true
    private var storeSearchHasMore = true

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(PublusInterceptor())
        addInterceptor {
            val request = it.request()
            val response = it.proceed(request)
            if (response.code == 400 && request.url.pathSegments.last() == "master") {
                throw IOException("Log in via WebView and purchase this volume to read.")
            }
            response
        }
    }

    override fun Headers.Builder.configureHeaders() = set("X-Requested-With", "pixivcomic")

    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page == 1) alreadyLoadedPopularMangaIds.clear()
        val count = pageSize * page
        val url = "$apiUrl/rankings/popularity".toHttpUrl().newBuilder()
            .addQueryParameter("label", "総合")
            .addQueryParameter("count", count.toString())
            .build()

        val ranking = client.get(url).parseAs<ApiResponse<PopularResponse>>().data.ranking
        val mangas = ranking
            .filter { alreadyLoadedPopularMangaIds.add(it.id) }
            .map { it.toSManga() }

        return MangasPage(mangas, mangas.isNotEmpty() && ranking.size >= count)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/works/recent_updates/v2".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        return client.get(url).toMangasPage()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = coroutineScope {
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
            return@coroutineScope client.get(url).toMangasPage()
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
                client.get(url).parseAs<ApiResponse<SeriesResponse>>().data
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
                client.get(url).parseAs<ApiResponse<StoreSearchResponse>>().data
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

    override val supportsFilterFetching = true

    override fun getFilterList(data: JsonElement?): FilterList {
        val categories = data?.parseAs<List<String>>().orEmpty()
        return FilterList(
            buildList {
                add(Filter.Header("Search query replaces the filters below!"))
                add(Filter.Header("Only one filter can be used at a time!"))
                add(TagsFilter())
                if (categories.isNotEmpty()) add(CategoryFilter(categories))
            },
        )
    }

    override suspend fun fetchFilterData() = client.get("$apiUrl/categories").parseAs<ApiResponse<CategoryResponse>>().data.categories.map { it.name }.toJsonElement()

    override fun getMangaUrl(manga: SManga): String = if (manga.memo["store"]?.stringOrNull == "1") {
        "$baseUrl/store/products/${manga.url}"
    } else {
        "$baseUrl/works/${manga.url}"
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        if (manga.memo["store"]?.stringOrNull == "1") {
            val product = try {
                fetchProduct(manga.url)
            } catch (_: Exception) {
                throw Exception("Log in via WebView and enable R-18 content at https://www.pixiv.net/settings/viewing")
            }
            val updatedManga = product.product.toSManga()
            val updatedChapters = product.variants
                .filter { !hideLocked || !it.isLocked }
                .map { it.toSChapter() }
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
            val episodeChapters = episodes!!.await()
                .filter { !hideLocked || !it.isLocked }
                .map { it.toSChapter() }

            val storeProductKey = officialWorkResult?.storeProductKey
            val volumeChapters = if (storeProductKey.isNullOrEmpty()) {
                emptyList()
            } else {
                try {
                    fetchVolumes(storeProductKey)
                        .filter { !hideLocked || !it.isLocked }
                        .map { it.toSChapter() }
                } catch (_: Exception) {
                    emptyList()
                }
            }

            episodeChapters + volumeChapters
        } else {
            chapters
        }

        SMangaUpdate(updatedManga, updatedChapters)
    }

    private suspend fun fetchOfficialWork(mangaId: String): OfficialWork = client.get("$apiUrl/works/v5/$mangaId").parseAs<ApiResponse<DetailsResponse>>().data.officialWork

    private suspend fun fetchEpisodes(mangaId: String): List<Episode> {
        val url = "$apiUrl/works/$mangaId/episodes/v2".toHttpUrl().newBuilder()
            .addQueryParameter("order", "desc")
            .build()
        return client.get(url)
            .parseAs<ApiResponse<ChapterResponse>>()
            .data.episodes
            .mapNotNull { it.episode }
    }

    private suspend fun fetchVolumes(storeProductKey: String): List<Variant> {
        val url = "$apiUrl/store/products/$storeProductKey/variants/v3".toHttpUrl().newBuilder()
            .addQueryParameter("order", "desc")
            .addQueryParameter("view_type", "product_detail")
            .build()
        return client.get(url, ensureSuccess = false)
            .parseAs<ApiResponse<VolumeResponse>>()
            .data.variants
    }

    private suspend fun fetchProduct(storeProductKey: String): ProductResponse = client.get("$apiUrl/store/products/v2/$storeProductKey", ensureSuccess = false).parseAs<ApiResponse<ProductResponse>>().data

    override fun getChapterUrl(chapter: SChapter): String = if (chapter.url.any { it.isLetter() }) {
        "$baseUrl/store/viewers/${chapter.url}/master"
    } else {
        "$baseUrl/viewer/stories/${chapter.url}"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterResponse = client.get(getChapterUrl(chapter))
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

            val content = client.get(cUrl).parseAs<PublusContent>()

            if (content.url.isNullOrEmpty()) {
                throw Exception("Log in via WebView and purchase this volume to read.")
            }

            return fetchPages(content.url!!, headers, client, content.authInfo?.toAuth(), hashFilenames = false)
        }

        val salt = chapterResponse.asJsoup().extractNextJs<SaltResponse>()?.props?.pageProps?.salt.orEmpty()
        val (time, hash) = getTimeAndHash(salt)
        val header = headersBuilder()
            .add("X-Client-Time", time)
            .add("X-Client-Hash", hash)
            .build()
        val pages = client.get("$apiUrl/episodes/${chapter.url}/read_v4", header)
            .parseAs<ApiResponse<ViewerResponse>>()
            .data.readingEpisode.pages
        if (pages.isNullOrEmpty()) {
            throw Exception("Log in via WebView and purchase this chapter to read.")
        }

        return pages.mapIndexed { i, page ->
            val url = page.url.toHttpUrl().newBuilder().removePathSegment(1).removePathSegment(0).build().toString()
            Page(i, imageUrl = url)
        }
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
