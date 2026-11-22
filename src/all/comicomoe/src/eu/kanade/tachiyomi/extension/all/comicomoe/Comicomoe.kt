package eu.kanade.tachiyomi.extension.all.comicomoe

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.call
import keiyoushi.network.fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Comicomoe :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    private val apiUrl get() = baseUrl.toHttpUrl().newBuilder().addPathSegment("api")

    private fun mangaApi(id: String) = apiUrl.addPathSegment("manga").addPathSegment(id)

    private val baseUrlHost get() = baseUrl.toHttpUrl().host

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3, 2.seconds) { it.host == baseUrlHost }
        .addInterceptor(::pageInterceptor)

    override suspend fun getPopularManga(page: Int): MangasPage = browse(page) {
        addQueryParameter("sort", "popular")
        contentRatings.forEach { addQueryParameter("contentRating", it) }
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = apiUrl.addPathSegments("stats/titles")
            .addQueryParameter("sort", "latest")
            // The site's homepage sends both; the list is slow without them
            .addQueryParameter("language", siteLanguage)
            .addQueryParameter("languages", siteLanguage)
            .apply { contentRatings.forEach { addQueryParameter("contentRating", it) } }
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("offset", ((page - 1) * PAGE_SIZE).toString())
            .build()
        val mangas = client.get(url, Fetch.XHR).parseAs<List<MangaDto>>()
        return MangasPage(mangas.map { it.toSManga(baseUrl) }, mangas.size == PAGE_SIZE)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = browse(page) {
        if (query.isNotBlank()) addQueryParameter("q", query.trim())
        filters.filterIsInstance<UrlFilter>().forEach { it.addToUrl(this) }
        // Relevance only ranks text matches
        if (query.isBlank() && filters.firstInstanceOrNull<SortFilter>()?.state == 0) setQueryParameter("sort", "popular")
    }

    private suspend fun browse(page: Int, params: HttpUrl.Builder.() -> Unit): MangasPage {
        val offset = (page - 1) * PAGE_SIZE
        val url = apiUrl.addPathSegment("manga")
            .addQueryParameter("source", "all")
            .apply(params)
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("offset", offset.toString())
            .build()
        val result = client.get(url, Fetch.XHR).parseAs<MangaListDto>()
        val hasNextPage = offset + PAGE_SIZE < result.total && offset + 2 * PAGE_SIZE <= MAX_RESULTS
        return MangasPage(result.items.map { it.toSManga(baseUrl) }, hasNextPage)
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val segments = url.pathSegments
        if (url.host != baseUrlHost || segments.firstOrNull() != "author" || segments.size < 2) {
            return super.getMangasByUrl(url, page)
        }
        val author = client.get(apiUrl.addPathSegment("authors").addPathSegment(segments[1]).build(), Fetch.XHR)
            .parseAs<AuthorTitlesDto>()
        return MangasPage(author.titles.map { it.toSManga(baseUrl) }, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrlHost) return null
        val segments = url.pathSegments
        val mangaId = when (segments.firstOrNull()) {
            "manga" -> segments.getOrNull(1)
            "reader" -> segments.getOrNull(1)?.let { chapterId ->
                client.get(apiUrl.addPathSegment("chapters").addPathSegment(chapterId).build(), Fetch.XHR)
                    .parseAs<ChapterInfoDto>().chapter.mangaId
            }
            else -> null
        } ?: return null
        val manga = SManga.create().apply { this.url = mangaId }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = SMangaUpdate(
        { if (fetchDetails) mangaDetails(manga) else manga },
        { if (fetchChapters) chapterList(manga.url) else chapters },
    )

    private suspend fun mangaDetails(manga: SManga): SManga = coroutineScope {
        val metadata = async { client.get(mangaApi(manga.url).addPathSegment("metadata").build(), Fetch.XHR).parseAs<MetadataDto>() }
        val details = client.get(mangaApi(manga.url).build(), Fetch.XHR).parseAs<MangaDto>()
        details.toSManga(baseUrl, metadata.await())
    }

    private suspend fun chapterList(mangaId: String): List<SChapter> = coroutineScope {
        client.get(mangaApi(mangaId).addPathSegment("sources").build(), Fetch.XHR)
            .parseAs<List<ProviderDto>>()
            .filter { it.readableChapterCount > 0 }
            .map { provider -> async { providerChapters(mangaId, provider.sourceId).map { it.toSChapter(provider.name) } } }
            .awaitAll()
            .flatten()
            .sortedByDescending { it.chapter_number }
    }

    private suspend fun providerChapters(mangaId: String, provider: String): List<ChapterDto> = coroutineScope {
        fun url(offset: Int) = mangaApi(mangaId).addPathSegment("chapters")
            .addQueryParameter("source", provider)
            .addQueryParameter("language", siteLanguage)
            .addQueryParameter("limit", CHAPTER_PAGE_SIZE.toString())
            .addQueryParameter("offset", offset.toString())
            .build()
        val first = client.get(url(0), Fetch.XHR).parseAs<ChapterListDto>()
        val rest = (CHAPTER_PAGE_SIZE until first.total step CHAPTER_PAGE_SIZE).map { offset ->
            async { client.get(url(offset), Fetch.XHR).parseAs<ChapterListDto>().items }
        }
        first.items + rest.awaitAll().flatten()
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("manga")
        .addPathSegment(manga.url)
        .build()
        .toString()

    override fun getChapterUrl(chapter: SChapter): String = chapterUrl(chapter.url)

    private fun chapterUrl(chapterId: String) = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("reader")
        .addPathSegment(chapterId)
        .build()
        .toString()

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val url = mangaApi(manga.url).addPathSegment("recommendations").build()
        return client.get(url, Fetch.XHR).parseAs<List<MangaDto>>().map { it.toSManga(baseUrl) }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val access = client.call(accessRequest(chapter.url)).parseAs<AccessDto>()
        val session = client.call(openRequest(chapter.url, access.token, null)).parseAs<SessionDto>()
        sessions.put(chapter.url, session)
        return session.pages.indices.map { index -> Page(index, imageUrl = session.imageUrl(chapter.url, index)) }
    }

    // Page urls carry "<chapter id>|<provider>|<CuuTruyen drmData>" in the fragment
    private fun SessionDto.imageUrl(chapterId: String, index: Int): String = baseUrl.toHttpUrl().resolve(pages[index])!!
        .newBuilder()
        .fragment(listOf(chapterId, readerSourceId, drmData.getOrNull(index)?.filterNot(Char::isWhitespace).orEmpty()).joinToString("|"))
        .build()
        .toString()

    private fun pageInterceptor(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val fragment = request.url.fragment?.split('|') ?: return chain.proceed(request)
        val (chapterId, provider) = fragment
        return try {
            var response = chain.proceed(request)
            // Reader sessions expire after a few minutes; the app keeps page urls in its page list cache
            repeat(2) {
                if (response.code != 410) return@repeat
                response.close()
                // Page path: /api/reader/pages/<session id>/<index>
                val (sessionId, index) = request.url.pathSegments.takeLast(2)
                request = request.newBuilder().url(reopenedSession(chapterId, provider, sessionId).imageUrl(chapterId, index.toInt())).build()
                response = chain.proceed(request)
            }
            val drmData = request.url.fragment!!.split('|')[2].ifEmpty { null }
            if (drmData == null || !response.isSuccessful) response else response.use { unscramble(it, drmData) }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException(e)
        }
    }

    private fun unscramble(response: Response, drmData: String): Response {
        val scrambled = response.body.byteStream().use(BitmapFactory::decodeStream)
            ?: throw IOException("Undecodable CuuTruyen page")
        val page = try {
            CuuTruyenDescrambler.unscramble(scrambled, drmData)
        } finally {
            scrambled.recycle()
        }
        val buffer = Buffer()
        try {
            page.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        } finally {
            page.recycle()
        }
        return response.newBuilder().body(buffer.asResponseBody(JPEG_MEDIA_TYPE)).build()
    }

    private val sessions = LruCache<String, SessionDto>(10)

    private val sessionLock = Mutex()

    // Blocking calls: async ones would queue behind the page loads holding this host's dispatcher slots.
    // The same provider keeps page indexes valid.
    private fun reopenedSession(chapterId: String, provider: String, expiredSessionId: String): SessionDto = runBlocking {
        sessionLock.withLock {
            sessions.get(chapterId)?.takeIf { it.sessionId != expiredSessionId && it.readerSourceId == provider }
                ?: run {
                    val access = client.newCall(accessRequest(chapterId)).execute().ensureSuccess().parseAs<AccessDto>()
                    client.newCall(openRequest(chapterId, access.token, provider)).execute().ensureSuccess()
                        .parseAs<SessionDto>()
                        .also { sessions.put(chapterId, it) }
                }
        }
    }

    private fun Response.ensureSuccess(): Response {
        if (isSuccessful) return this
        close()
        throw IOException("HTTP error $code")
    }

    private fun accessRequest(chapterId: String) = Request.Builder()
        .url(apiUrl.addPathSegments("reader/access").addPathSegment(chapterId).build())
        .headers(headers)
        // A fresh token each time: a revalidated cached one would be expired
        .cacheControl(CacheControl.FORCE_NETWORK)
        .fetch(Fetch.XHR)
        .build()

    private fun openRequest(chapterId: String, token: String, readerSourceId: String?) = Request.Builder()
        .url(apiUrl.addPathSegments("reader/chapters").addPathSegment(chapterId).addPathSegment("open").addQueryParameter("d", token).build())
        .headers(headers)
        // "all": the site proxies every provider's pages
        .post(OpenBodyDto("all", readerSourceId).toJsonRequestBody())
        .fetch(Fetch.XHR.from(chapterUrl(chapterId)))
        .build()

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get(apiUrl.addPathSegment("tags").build(), Fetch.XHR)
        .parseAs<List<TagDto>>()
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val tags = data?.parseAs<List<TagDto>>().orEmpty()
        return FilterList(
            listOf(
                SortFilter(),
                StatusFilter(),
                FormatFilter(),
                DemographicFilter(),
                ContentRatingFilter(contentRatings),
                ComickTagsFilter(),
            ) + tags.groupBy { it.group }.map { (group, groupTags) -> TagFilter(group, groupTags.sortedBy { it.name }) },
        )
    }

    private val contentRatings: Set<String>
        get() = preferences.getStringSet(PREF_CONTENT_RATING, DEFAULT_CONTENT_RATINGS)!!

    private val siteLanguage get() = SITE_LANGUAGES[lang] ?: lang

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        MultiSelectListPreference(screen.context).apply {
            key = PREF_CONTENT_RATING
            title = "Content rating"
            summary = "Shown in popular, latest and search"
            entries = CONTENT_RATINGS.map { it.first }.toTypedArray()
            entryValues = CONTENT_RATINGS.map { it.second }.toTypedArray()
            setDefaultValue(DEFAULT_CONTENT_RATINGS)
        }.also(screen::addPreference)
    }

    companion object {
        private const val PAGE_SIZE = 24
        private const val CHAPTER_PAGE_SIZE = 100

        private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()

        // The API refuses offset + limit above this
        private const val MAX_RESULTS = 10000

        private const val PREF_CONTENT_RATING = "content_rating"
        private val DEFAULT_CONTENT_RATINGS = setOf("safe", "suggestive")

        private val SITE_LANGUAGES = mapOf(
            "es-419" to "es-la",
            "fil" to "tl",
            "pt-BR" to "pt-br",
            "zh-Hans" to "zh",
            "zh-Hant" to "zh-hk",
        )
    }
}
