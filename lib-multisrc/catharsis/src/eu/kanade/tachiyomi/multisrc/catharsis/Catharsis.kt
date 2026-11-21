package eu.kanade.tachiyomi.multisrc.catharsis

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.applicationContext
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.source
import java.io.File
import java.io.IOException
import java.time.Duration
import kotlin.time.Duration.Companion.minutes

/** A site read only through its own pages in a WebView: listings, series and chapters, covers and pages. */
abstract class Catharsis : KeiSource() {

    // First path segment of the series list and of a series page
    protected abstract val listingPath: String
    protected abstract val mangaPath: String

    // What comes between the series and the chapter number in a chapter's path
    protected abstract val chapterSegment: String

    // Hosts the site sends its chapter reader to
    protected open val readerHosts: Set<String> = emptySet()

    // An asset run in the reading world before each page's own scripts
    protected open val prelude: String? = null

    // The series list's sort, status and order values, as its page url takes them
    protected abstract val sorts: List<Pair<String, String?>>
    protected abstract val popularSort: Int
    protected abstract val latestSort: Int
    protected abstract val statuses: List<String>
    protected abstract val ascendingOrder: String

    /** Reads the series list page at [url]. */
    protected abstract suspend fun readListing(url: HttpUrl): MangasPage

    /** Reads the series page of [manga], with its chapter list when [withChapters]; [known] are the chapters stored. */
    protected abstract suspend fun readSeries(manga: SManga, known: List<SChapter>, withChapters: Boolean): SMangaUpdate

    // The reader's page elements: each a canvas or holding one, in reading order
    protected abstract val readerPages: String

    // The site's reader for text-only chapters (novels), if it has one
    protected open val readerText: String? = null

    /** What to tell the user when the reader shows [text] instead of pages, or null while it is still loading. */
    protected abstract fun readerRefusal(text: String): String?

    // A page waits on the reader's WebView, longer than the app's call timeout
    final override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::webViewInterceptor)
        .callTimeout(Duration.ZERO)

    // Lazy for the source's own hosts and id; baseUrl is read on each load
    protected val siteView by lazy { SiteView({ baseUrl }, readerHosts, prelude) { headers["User-Agent"]!! } }

    override suspend fun getPopularManga(page: Int) = readListing(listingUrl(page, "", popularSort, null, null, emptyList()))

    override suspend fun getLatestUpdates(page: Int) = readListing(listingUrl(page, "", latestSort, null, null, emptyList()))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()
        return readListing(
            listingUrl(
                page = page,
                query = query,
                sort = sort?.state?.index ?: popularSort,
                ascending = sort?.state?.ascending,
                status = filters.firstInstanceOrNull<StatusFilter>()?.value,
                genres = filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty().filter { it.state }.map { it.id },
                letter = filters.firstInstanceOrNull<LetterFilter>()?.value,
            ),
        )
    }

    private fun listingUrl(page: Int, query: String, sort: Int, ascending: Boolean?, status: String?, genres: List<String>, letter: String? = null) = baseUrl.toHttpUrl().newBuilder().apply {
        addPathSegment(listingPath)
        if (page > 1) addQueryParameter("page", page.toString())
        if (query.isNotBlank()) addQueryParameter("search", query.trim())
        letter?.let { addQueryParameter("letter", it) }
        status?.let { addQueryParameter("status", it) }
        sorts[sort].second?.let { addQueryParameter("sort", it) }
        if (ascending == true) addQueryParameter("order", ascendingOrder)
        if (genres.isNotEmpty()) addQueryParameter("genre", genres.joinToString("|"))
    }.build()

    override fun getMangaUrl(manga: SManga) = "$baseUrl/$mangaPath/${manga.url}"

    // Stored as <series>/<chapter number>
    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/$mangaPath/${chapter.url.substringBeforeLast('/')}/$chapterSegment${chapter.url.substringAfterLast('/')}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.getOrNull(0) != mangaPath) return null
        val key = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = key }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = readSeries(manga, chapters, fetchChapters)

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val count = reader(chapterUrl.toHttpUrl().encodedPath, 0).pageCount.await()
        return List(count) { index -> Page(index, imageUrl = "$chapterUrl#$PAGE_FRAGMENT$index") }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        buildList {
            add(SortFilter(sorts.map { it.first }, popularSort))
            add(StatusFilter(statuses))
            add(LetterFilter())
            genres(data).takeIf { it.isNotEmpty() }?.let { add(GenreFilter(it.map { (name, id) -> GenreCheckBox(name, id) })) }
        },
    )

    /** The site's genres from the fetched filter data, as name to id. */
    protected open fun genres(data: JsonElement?): List<Pair<String, String>> = emptyList()

    private val readerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val readers = linkedMapOf<String, ChapterReader>()

    /** The chapter's open reader that can still deliver page [index], or a new one starting there. */
    @Synchronized
    private fun reader(chapterPath: String, index: Int): ChapterReader {
        readers[chapterPath]?.takeIf { it.covers(index) }?.let { return it }
        readers.remove(chapterPath)?.close()
        while (readers.size >= MAX_READERS) readers.remove(readers.keys.first())?.close()
        val dir = File(applicationContext.cacheDir, "catharsis-pages/$id/${chapterPath.hashCode().toUInt()}-$index")
        return ChapterReader("$baseUrl$chapterPath", index, siteView, dir, readerScope, readerPages, readerText, ::readerRefusal).also { readers[chapterPath] = it }
    }

    private val covers by lazy { Covers({ baseUrl }, File(applicationContext.cacheDir, "catharsis-covers/$id"), readerScope) { headers["User-Agent"]!! } }

    /** Pages come from the reader's WebView and covers from a WebView on the site, never from the network. */
    private fun webViewInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != baseUrl.toHttpUrl().host) throw IOException("Esta fuente solo carga desde las páginas del sitio")
        val index = request.url.fragment?.takeIf { it.startsWith(PAGE_FRAGMENT) }?.removePrefix(PAGE_FRAGMENT)?.toIntOrNull()
        if (index != null) {
            val file = awaitFile { reader(request.url.encodedPath, index).page(index).await() }
            return fileResponse(request, file, "image/jpeg")
        }
        if (request.url.pathSegments.first() != "assets") throw IOException("Esta fuente solo carga desde las páginas del sitio")
        val cover = awaitFile { covers.get(request.url.newBuilder().query(null).fragment(null).build().toString()).await() }
        return fileResponse(request, cover.file, cover.type)
    }

    private fun <T> awaitFile(block: suspend () -> T): T = try {
        runBlocking { withTimeout(FILE_TIMEOUT) { block() } }
    } catch (e: Throwable) {
        throw e as? IOException ?: IOException(e.message ?: "La página del sitio no cargó", e)
    }

    private fun fileResponse(request: Request, file: File, type: String) = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(file.source().buffer().asResponseBody(type.toMediaType(), file.length()))
        .build()
}

private const val PAGE_FRAGMENT = "catharsis-page-"
private const val MAX_READERS = 2
private val FILE_TIMEOUT = 5.minutes
