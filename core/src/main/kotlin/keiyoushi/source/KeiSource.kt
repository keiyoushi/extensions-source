package keiyoushi.source

import android.util.Log
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.applicationContext
import keiyoushi.utils.jsonInstance
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.json.okio.encodeToBufferedSink
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.brotli.BrotliInterceptor
import okio.GzipSink
import okio.GzipSource
import okio.buffer
import okio.sink
import okio.source
import rx.Observable
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.days

abstract class KeiSource : HttpSource() {

    /**
     * Customizes the OkHttpClient builder. Subclasses can override this to configure timeouts,
     * add application/network interceptors, or configure cookie/cache/dns settings.
     */
    protected open fun OkHttpClient.Builder.configureClient() = this

    final override val client: OkHttpClient by lazy {
        network.client.newBuilder().apply {
            val networkInterceptors = networkInterceptors()

            networkInterceptors
                .indexOfFirst { it.javaClass.simpleName == "IgnoreGzipInterceptor" }
                .takeIf { it != -1 }
                ?.also { networkInterceptors.removeAt(it) }

            networkInterceptors
                .indexOfFirst { it is BrotliInterceptor }
                .takeIf { it != -1 }
                ?.also { networkInterceptors.removeAt(it) }

            configureClient()

            // last application interceptor
            addInterceptor(BrotliInterceptor)
        }.build()
    }

    /**
     * Customizes the base Headers builder. Subclasses can override this to append source-specific
     * headers.
     *
     * Note: "User-Agent", "Referer", and "Origin" are already added by default.
     */
    protected open fun Headers.Builder.configureHeaders(): Headers.Builder = this

    final override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Origin", baseUrl)
        .configureHeaders()

    /**
     * Fetches a page of popular manga from the source.
     *
     * @param page The page number to retrieve.
     * @return A [MangasPage] containing the list of manga and whether there is a next page.
     */
    abstract override suspend fun getPopularManga(page: Int): MangasPage

    /**
     * Whether the source supports fetching a list of latest manga updates.
     */
    override val supportsLatest get() = true

    /**
     * Fetches a page of latest manga updates from the source.
     *
     * @param page The page number to retrieve.
     * @return A [MangasPage] containing the list of updated manga and whether there is a next page.
     */
    abstract override suspend fun getLatestUpdates(page: Int): MangasPage

    /**
     * Fetches a page of manga matching the query and filters.
     *
     * @param page The page number to retrieve.
     * @param query The search query.
     * @param filterList The list of filters to apply.
     * @return A [MangasPage] containing the matching manga and whether there is a next page.
     */
    abstract suspend fun getSearchMangaList(page: Int, query: String, filterList: FilterList): MangasPage

    /**
     * Fetches details of a single manga directly using its HTTP URL.
     * Used for resolving URL search queries in the browser or search bar.
     *
     * @param url The [HttpUrl] of the manga.
     * @return The [SManga] details if resolved successfully, or null.
     */
    abstract suspend fun getMangaByUrl(url: HttpUrl): SManga?

    final override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        query.toHttpUrlOrNull()?.also { url ->
            val manga = getMangaByUrl(url)

            return MangasPage(listOfNotNull(manga), hasNextPage = false)
        }

        return getSearchMangaList(page, query, filters)
    }

    private val filterFetchInFlight = AtomicBoolean(false)

    private val filterFetchAttemptCount = AtomicInteger(0)

    private val maxFilterFetchAttempts = 3

    private val filterCacheDir: File by lazy {
        applicationContext.cacheDir.resolve("source_$id").apply { mkdirs() }
    }

    private val filterCacheFile: File by lazy { filterCacheDir.resolve("filters.json.gz") }

    /**
     * Whether this source fetches its filters from the network (vs. having a static,
     * hardcoded [FilterList]). When false, none of the caching/background-fetch machinery
     * below runs — [getFilterList] just calls [getFilterList] with `null` directly — and
     * [fetchFilterData] don't need to be implemented.
     */
    protected open val supportsFilterFetching: Boolean = false

    /**
     * Fetches raw filter data from the network.
     *
     * Only called when [supportsFilterFetching] is true, on a background coroutine —
     * never on the calling thread of [getFilterList]. Implementations should perform
     * whatever requests are necessary (e.g. scraping a search/filter page) and return
     * the result as a [JsonElement], or null if the source genuinely has no dynamic
     * filters to offer.
     *
     * Throwing is treated as a failed attempt and retried (see [maxFilterFetchAttempts]);
     * returning null is treated as "no filters" and is not retried.
     */
    protected open suspend fun fetchFilterData(): JsonElement = JsonNull

    /**
     * Builds a [FilterList] from previously cached/fetched filter data.
     *
     * This is called synchronously every time [getFilterList] is invoked, so it must be
     * fast and must NOT perform I/O or network requests — only convert [data] into filters.
     *
     * @param data The cached filter data, freshly fetched data, or null when no (fresh)
     *  data is available yet (e.g. first launch, cache expired and fetch still pending in
     *  the background, fetching failed permanently, or the source returned no data).
     */
    protected open fun getFilterList(data: JsonElement?): FilterList = FilterList()

    final override fun getFilterList(): FilterList {
        if (!supportsFilterFetching) return getFilterList(data = null)

        val (cached, isFresh) = readFilterCacheState()
        val parsed = cached?.let {
            try {
                getFilterList(it)
            } catch (e: Throwable) {
                Log.e(name, "Failed to parse filter data", e)
                null
            }
        }

        if (parsed == null || !isFresh) {
            triggerBackgroundFilterFetch()
        }

        if (parsed != null) return parsed

        val hint = if (filterFetchAttemptCount.get() >= maxFilterFetchAttempts) {
            Filter.Header("Failed to fetch filters, restart the app to retry")
        } else {
            Filter.Header("Press 'Reset' to fetch more filters")
        }
        return FilterList(getFilterList(data = null) + Filter.Separator() + hint)
    }

    private data class FilterCacheState(val data: JsonElement?, val isFresh: Boolean)

    private fun readFilterCacheState(): FilterCacheState {
        val file = filterCacheFile
        if (!file.exists()) return FilterCacheState(data = null, isFresh = false)

        val age = System.currentTimeMillis() - file.lastModified()
        val isFresh = age <= 1.days.inWholeMilliseconds

        val data = try {
            GzipSource(file.source()).buffer().use { source ->
                jsonInstance.decodeFromBufferedSource(JsonElement.serializer(), source)
            }
        } catch (_: Throwable) {
            null
        }

        return FilterCacheState(data, isFresh)
    }

    private fun writeFilterCache(data: JsonElement) {
        filterCacheDir.mkdirs()
        val tmpFile = File.createTempFile("filters", ".tmp", filterCacheDir)

        try {
            GzipSink(tmpFile.sink()).buffer().use { sink ->
                jsonInstance.encodeToBufferedSink(JsonElement.serializer(), data, sink)
            }

            if (!tmpFile.renameTo(filterCacheFile)) {
                filterCacheFile.delete()
                if (!tmpFile.renameTo(filterCacheFile)) {
                    throw IOException("Failed to move $tmpFile to $filterCacheFile")
                }
            }
        } finally {
            tmpFile.delete()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun triggerBackgroundFilterFetch() {
        if (filterFetchAttemptCount.get() >= maxFilterFetchAttempts) return
        if (!filterFetchInFlight.compareAndSet(false, true)) return

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val data = fetchFilterData()
                writeFilterCache(data)
                filterFetchAttemptCount.set(0)
            } catch (e: Throwable) {
                Log.e(name, "Failed to fetch filter data", e)
                filterFetchAttemptCount.incrementAndGet()
            } finally {
                filterFetchInFlight.set(false)
            }
        }
    }

    /**
     * Fetches updated information for a manga.
     *
     * Depending on the provided flags or source availability, this may include
     * updated manga metadata, available chapters, or both.
     *
     * If a value is not requested, the existing provided value can be returned as-is.
     * The host app may apply any returned updates regardless of the flags,
     * so it’s best to avoid returning unintended or inaccurate data.
     *
     * @param manga The manga to fetch updates for.
     * @param chapters Existing chapters of the manga
     * @param fetchDetails Whether to include updated manga details.
     * @param fetchChapters Whether to include available chapters.
     */
    abstract override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate

    /**
     * Whether the source supports retrieving related manga.
     */
    override val supportsRelatedMangas = true

    /**
     * Whether to fall back to searching the source by the manga's title if a direct related manga list is unavailable.
     */
    protected open val supportRelatedMangasBySearch = false

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    final override val disableRelatedMangasBySearch get() = !supportRelatedMangasBySearch

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    final override val disableRelatedMangas = false

    /**
     * Fetches a list of related manga for the specified manga.
     *
     * @param manga The reference manga.
     * @return A list of related [SManga].
     */
    abstract override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga>

    /**
     * Returns the absolute web URL for the provided manga.
     * Used for the WebView ("Open in WebView") and sharing features.
     *
     * @param manga The manga.
     * @return The absolute URL of the manga.
     */
    abstract override fun getMangaUrl(manga: SManga): String

    /**
     * Returns the absolute web URL for the provided chapter.
     * Used for the WebView ("Open in WebView") and sharing features.
     *
     * @param chapter The chapter.
     * @return The absolute URL of the chapter.
     */
    abstract override fun getChapterUrl(chapter: SChapter): String

    /**
     * Get the list of pages a chapter has. Pages should be returned
     * in the expected order; the index is ignored.
     *
     * @param chapter the chapter.
     * @return the pages for the chapter.
     */
    abstract override suspend fun getPageList(chapter: SChapter): List<Page>

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    @Suppress("DEPRECATION")
    final override fun fetchPopularManga(page: Int): Observable<MangasPage> = super.fetchPopularManga(page)

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    final override fun popularMangaRequest(page: Int) = throw UnsupportedOperationException()

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    final override fun popularMangaParse(response: Response) = throw UnsupportedOperationException()

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    @Suppress("DEPRECATION")
    final override fun fetchLatestUpdates(page: Int): Observable<MangasPage> = super.fetchLatestUpdates(page)

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    final override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    final override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    @Suppress("DEPRECATION")
    final override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = super.fetchSearchManga(page, query, filters)

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    final override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    final override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    @Suppress("DEPRECATION")
    final override fun fetchMangaDetails(manga: SManga): Observable<SManga> = super.fetchMangaDetails(manga)

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    final override fun mangaDetailsRequest(manga: SManga) = throw UnsupportedOperationException()

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    final override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException()

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    final override fun relatedMangaListRequest(manga: SManga) = throw UnsupportedOperationException()

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    final override fun relatedMangaListParse(response: Response): List<SManga> = throw UnsupportedOperationException()

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    @Suppress("DEPRECATION")
    final override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = super.fetchChapterList(manga)

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    final override fun chapterListRequest(manga: SManga) = throw UnsupportedOperationException()

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    final override fun chapterListParse(response: Response) = throw UnsupportedOperationException()

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    @Suppress("DEPRECATION")
    final override fun prepareNewChapter(chapter: SChapter, manga: SManga) = super.prepareNewChapter(chapter, manga)

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    @Suppress("DEPRECATION")
    final override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = super.fetchPageList(chapter)

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    final override fun pageListRequest(chapter: SChapter) = throw UnsupportedOperationException()

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    final override fun pageListParse(response: Response) = throw UnsupportedOperationException()

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    @Suppress("DEPRECATION")
    final override fun fetchImageUrl(page: Page): Observable<String> = super.fetchImageUrl(page)

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    final override fun imageUrlRequest(page: Page) = throw UnsupportedOperationException()

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    final override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
