package eu.kanade.tachiyomi.extension.en.renascans

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.intOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Instant

@Source
abstract class Renascans :
    KeiSource(),
    ConfigurableSource {

    override val supportsFilterFetching = true

    override val supportsRelatedMangas = true

    private val preferences by getPreferencesLazy()

    private val showLocked get() = preferences.getBoolean(PREF_SHOW_LOCKED, false)

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::resignExpiredPage)

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter("popular")))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter("newest")))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val offset = (page - 1) * PAGE_SIZE
        val genres = filters.firstInstanceOrNull<GenreFilter>()
        val url = apiUrl.newBuilder().apply {
            addPathSegment("manga")
            addQueryParameter("limit", PAGE_SIZE.toString())
            addQueryParameter("offset", offset.toString())
            filters.firstInstanceOrNull<SortFilter>()?.let { addQueryParameter("sort", it.value) }
            if (query.isNotBlank()) addQueryParameter("q", query.trim())
            genres?.included?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("genres", it.joinToString(",")) }
            genres?.excluded?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("exclude_genres", it.joinToString(",")) }
            filters.filterIsInstance<TextQueryFilter>().forEach { filter ->
                filter.state.trim().takeIf { it.isNotEmpty() }?.let { addQueryParameter(filter.param, it) }
            }
        }.build()

        val response = client.get(url, Fetch.XHR)
        val total = response.header("X-Total-Count")!!.toInt()
        val mangas = response.parseAs<List<MangaDto>>().map { it.toSManga(baseUrl) }

        return MangasPage(mangas, offset + mangas.size < total)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() !in setOf("manga", "series")) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null

        return getMangaDetails(slug).toSManga(baseUrl).apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.slug
        val details = getMangaDetails(slug)
        val chapterList = details.chapters
            .filter { showLocked || !it.isLocked }
            .sortedByDescending { it.chapterNumber }
            .map { it.toSChapter(slug) }

        return SMangaUpdate(details.toSManga(baseUrl), chapterList)
    }

    private suspend fun getMangaDetails(slug: String): MangaDetailsDto {
        val url = apiUrl.newBuilder().addPathSegment("manga").addPathSegment(slug).build()
        return client.get(url, Fetch.XHR).parseAs()
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl.toHttpUrl().newBuilder().addPathSegment("manga").addPathSegment(manga.slug).build().toString()

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val url = apiUrl.newBuilder()
            .addPathSegment("manga")
            .addPathSegment(manga.slug)
            .addPathSegment("similar")
            .addQueryParameter("limit", "30")
            .build()

        return client.get(url, Fetch.XHR).parseAs<List<MangaDto>>().map { it.toSManga(baseUrl) }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val id = chapter.memo[MEMO_CHAPTER_ID]?.intOrNull?.toString() ?: throw Exception("Refresh the chapter list")
        val pages = client.get(chapterPagesUrl(id), Fetch.XHR.from(getChapterUrl(chapter))).parseAs<PagesDto>().content

        if (pages.isEmpty()) throw Exception("Log in and unlock this chapter in WebView")

        return pages.mapIndexed { index, path ->
            Page(index, imageUrl = imageUrl(path).newBuilder().fragment(id).build().toString())
        }
    }

    private fun chapterPagesUrl(chapterId: String) = apiUrl.newBuilder().addPathSegment("chapters").addPathSegment(chapterId).build()

    private fun imageUrl(path: String) = baseUrl.toHttpUrl().resolve(path.toUploadPath())!!

    // Page links are signed for up to 30 minutes; the fragment holds the chapter id
    private fun resignExpiredPage(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val chapterId = request.url.fragment ?: return chain.proceed(request)
        val response = chain.proceed(request)
        if (response.code != 403) return response
        response.close()

        val url = resignedPage(chapterId, request.url) ?: throw IOException("Page not found in chapter $chapterId")
        return chain.proceed(request.newBuilder().url(url).build())
    }

    // Last re-signed chapter, so its other expired pages need no further request
    private var resigned: ResignedPages? = null

    @Synchronized
    private fun resignedPage(chapterId: String, expired: HttpUrl): HttpUrl? {
        val path = expired.encodedPath
        resigned?.takeIf { it.chapterId == chapterId }
            ?.urls?.firstOrNull { it.encodedPath == path }
            ?.takeIf { it.query != expired.query && it.isSigned() }
            ?.let { return it }
        val urls = try {
            val response = client.newCall(GET(chapterPagesUrl(chapterId), headers).newBuilder().fetch(Fetch.XHR).build()).execute()
            if (!response.isSuccessful) {
                response.close()
                throw IOException("HTTP error ${response.code}")
            }
            response.parseAs<PagesDto>().content.map(::imageUrl)
        } catch (e: Exception) {
            throw e as? IOException ?: IOException(e)
        }
        resigned = ResignedPages(chapterId, urls)
        return urls.firstOrNull { it.encodedPath == path }
    }

    private class ResignedPages(val chapterId: String, val urls: List<HttpUrl>)

    private fun HttpUrl.isSigned() = queryParameter("exp")?.toLongOrNull()?.let { Instant.fromEpochSeconds(it) > Clock.System.now() } == true

    override suspend fun fetchFilterData(): JsonElement = client.get(apiUrl.newBuilder().addPathSegment("genres").build(), Fetch.XHR)
        .parseAs<List<GenreDto>>()
        .map { it.title }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        buildList {
            add(SortFilter("popular"))
            data?.parseAs<List<String>>()?.let { add(GenreFilter(it)) }
            add(TextQueryFilter("Minimum chapters", "min_chapters"))
            add(TextQueryFilter("Maximum chapters", "max_chapters"))
            add(TextQueryFilter("Added after (YYYY-MM-DD)", "created_after"))
            add(TextQueryFilter("Added before (YYYY-MM-DD)", "created_before"))
        },
    )

    private val apiUrl get() = "$baseUrl/api/v1".toHttpUrl()

    // Iken stored "slug#postId"
    private val SManga.slug get() = url.substringBefore('#')

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_LOCKED
            title = "Show locked chapters"
            summary = "Coin chapters, readable after unlocking them on the site in WebView"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val PAGE_SIZE = 24

        // Iken's key, so the setting carries over
        private const val PREF_SHOW_LOCKED = "pref_show_locked_chapters"
    }
}
