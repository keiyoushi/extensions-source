package eu.kanade.tachiyomi.extension.pt.onereader

import android.util.LruCache
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.HttpException
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
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.aesGcm
import keiyoushi.utils.boolean
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.xor
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.BufferedSource
import okio.buffer
import okio.source
import java.io.IOException
import java.io.SequenceInputStream
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Source
abstract class OneReader :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::pageInterceptor)
        .rateLimit(2) { "pages" in it.pathSegments }
        .rateLimit(3)

    private val apiUrl get() = baseUrl.toHttpUrl().newBuilder().addPathSegments("api/reader")

    override suspend fun getPopularManga(page: Int): MangasPage = catalog(page) {
        addQueryParameter("sort", "VIEWS")
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = apiUrl.addPathSegments("home/updates")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "20")
            .addQueryParameter("filter", "all")
            .build()

        val updates = client.get(url, Fetch.XHR).parseAs<UpdatesDto>()

        return MangasPage(updates.items.map(WorkDto::toSManga), updates.pagination.hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = catalog(page) {
        addQueryParameter("sort", filters.firstInstance<SortFilter>().selected())
        if (query.isNotBlank()) addQueryParameter("q", query.trim())
        filters.firstInstance<FormatFilter>().selected().takeIf { it != "ALL" }?.let { addQueryParameter("format", it) }
        filters.firstInstance<StatusFilter>().selected().takeIf { it != "ALL" }?.let { addQueryParameter("status", it) }

        val genres = filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty()
        val included = genres.filter { it.state == Filter.TriState.STATE_INCLUDE }
        included.forEach { addQueryParameter("genre", it.name) }
        genres.filter { it.state == Filter.TriState.STATE_EXCLUDE }.forEach { addQueryParameter("excludeGenre", it.name) }
        if (included.size > 1 && filters.firstInstance<AllGenresFilter>().state) addQueryParameter("genreMode", "all")
    }

    private suspend fun catalog(page: Int, block: HttpUrl.Builder.() -> Unit): MangasPage {
        val url = apiUrl.addPathSegments("catalog/page")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .apply(block)
            .build()

        val catalog = client.get(url, Fetch.XHR).parseAs<CatalogDto>()

        return MangasPage(catalog.works.map(WorkDto::toSManga), catalog.pagination.hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.first().removeSuffix(".html") !in MANGA_PATHS) return null
        val mangaKey = url.queryParameter("id")?.takeIf(String::isNotEmpty) ?: return null

        return getMangaUpdate(SManga.create().apply { this.url = mangaKey }, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/obra".toHttpUrl().newBuilder()
        .addQueryParameter("id", manga.url)
        .build()
        .toString()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = client.get(apiUrl.addPathSegments("works").addPathSegment(manga.url).build(), Fetch.XHR)
            .parseAs<WorkDetailsDto>()
        val showLocked = preferences.getBoolean(SHOW_LOCKED_PREF, false)

        return SMangaUpdate(
            manga = details.work.toSManga(),
            chapters = details.chapters
                .filter { showLocked || !it.accessLocked }
                .map { it.toSChapter(manga.url) },
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/leitor".toHttpUrl().newBuilder()
        .addQueryParameter("id", chapter.memo["id"]!!.string)
        .addQueryParameter("cap", chapter.memo["number"]!!.string)
        .build()
        .toString()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.memo["locked"]?.boolean == true) return emptyList()

        val mangaKey = chapter.memo["id"]?.string ?: throw Exception("Refresh the chapter list")
        val number = chapter.memo["number"]!!.string
        val session = readerMutex.withLock { sessions.get("$mangaKey/$number") ?: openChapter(mangaKey, number) }

        return session.pageUrls.indices.map { index ->
            val imageUrl = HttpUrl.Builder()
                .scheme("https")
                .host(PAGE_HOST)
                .addPathSegment(mangaKey)
                .addPathSegment(number)
                .addPathSegment((index + 1).toString())
                .build()

            Page(index, imageUrl = imageUrl.toString())
        }
    }

    private val channel by lazy(::ReaderChannel)

    // The site's page window refuses concurrent walks, so page and manifest calls run one at a time
    private val readerMutex = Mutex()

    private val sessions = LruCache<String, ChapterSession>(4)

    private var blockedUntil = Instant.DISTANT_PAST

    private class ChapterSession(val pageUrls: List<String>, val serverKey: String?)

    // Caller holds readerMutex
    private suspend fun openChapter(mangaKey: String, number: String, rejectedPageUrl: HttpUrl? = null, page: Int = 0): ChapterSession {
        val chapterUrl = apiUrl.addPathSegments("works").addPathSegment(mangaKey)
            .addPathSegment("chapters").addPathSegment(number)
            .build()
        val transportHeaders = channel.addTransport(headers.newBuilder()).build()
        var url = rejectedPageUrl?.queryParameter("g")?.let {
            chapterUrl.newBuilder()
                .addQueryParameter("_or_pg", it)
                .addQueryParameter("_or_page", page.toString())
                .build()
        } ?: chapterUrl

        repeat(ATTEMPTS) {
            ensureNotBlocked()
            val response = client.get(url, transportHeaders, Fetch.XHR, ensureSuccess = false)
            if (response.isSuccessful) {
                val manifest = response.parseAs<ManifestDto>()
                return ChapterSession(manifest.chapter.pages, manifest.protection.transport?.serverKey)
                    .also { sessions.put("$mangaKey/$number", it) }
            }

            // A grant from an older site session can't be refreshed
            if (response.readError() == "READER_PROGRESS_GRANT_INVALID") url = chapterUrl
        }

        throw IOException("Chapter $number refused")
    }

    private fun pageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != PAGE_HOST) return chain.proceed(request)

        val (mangaKey, number, page) = request.url.pathSegments

        return try {
            fetchImage(chain, runBlocking { readerMutex.withLock { fetchMedia(chain, mangaKey, number, page.toInt()) } })
        } catch (e: HttpException) {
            throw IOException(e.message, e)
        }
    }

    // Caller holds readerMutex
    private suspend fun fetchMedia(chain: Interceptor.Chain, mangaKey: String, number: String, page: Int): MediaDto {
        var session = sessions.get("$mangaKey/$number") ?: openChapter(mangaKey, number)

        repeat(ATTEMPTS) {
            ensureNotBlocked()
            val pageUrl = baseUrl.toHttpUrl().resolve(session.pageUrls[page - 1])!!
            val metaHeaders = headers.newBuilder()
                .apply { session.serverKey?.let { channel.addProof(this, pageUrl, it) } }
                .set("Accept", MEDIA_ACCEPT)
                .build()
            val response = chain.proceed(Request.Builder().url(pageUrl).headers(metaHeaders).fetch(Fetch.XHR).build())

            if (response.isSuccessful) return response.parseAs<MediaDto>()

            // A refreshed session replaces the grant for every page
            if (response.readError() in MEDIA_REFRESH_CODES) session = openChapter(mangaKey, number, pageUrl, page)
        }

        throw IOException("Page $page refused")
    }

    private fun ensureNotBlocked() {
        if (Clock.System.now() < blockedUntil) throw IOException("Rate limited by the site, try again shortly")
    }

    // Reads the site's error code, waits out window pauses and stops all calls on its rate limits
    private suspend fun Response.readError(): String {
        val retryAfter = (header("Retry-After")?.toLongOrNull() ?: 0).seconds
        val errorCode = use {
            if (header("Content-Type")?.startsWith("application/json") == true) parseAs<ErrorDto>().code else null
        } ?: throw HttpException(code)

        when (errorCode) {
            in MEDIA_REFRESH_CODES -> delay(retryAfter)
            "READER_PROGRESS_GRANT_INVALID" -> Unit
            "READER_PAGE_WINDOW_WAIT" -> delay(maxOf(750.milliseconds, retryAfter))
            "READER_RATE_LIMITED", "READER_COOLDOWN" -> {
                blockedUntil = Clock.System.now() + maxOf(1.seconds, retryAfter)
                throw IOException(errorCode)
            }
            else -> throw IOException(errorCode)
        }

        return errorCode
    }

    private fun fetchImage(chain: Interceptor.Chain, media: MediaDto): Response {
        // Signed media urls answer Forbidden to anything with browser navigation headers
        val imageRequest = Request.Builder()
            .url(media.url)
            .header("Accept", IMAGE_ACCEPT)
            .header("User-Agent", chain.request().header("User-Agent")!!)
            .build()
        val response = chain.proceed(imageRequest)

        if (!response.isSuccessful) {
            response.close()
            throw HttpException(response.code)
        }

        val source = response.body.source()
        val image = when (media.mode) {
            "aes-gcm-v4" -> source.decodeGcmEnvelope(media)
            "xor-prefix-v3" -> source.decodeXorEnvelope(media)
            else -> {
                response.close()
                throw IOException("Unknown image mode ${media.mode}")
            }
        }

        return response.newBuilder()
            .body(image.asResponseBody(media.contentType?.toMediaType()))
            .build()
    }

    private fun BufferedSource.decodeGcmEnvelope(media: MediaDto): BufferedSource {
        skip(MAGIC_SIZE)
        val nonce = media.url.toHttpUrl().queryParameter("or_n")!!.decodeBase64Url()
        val cipher = aesGcm(channel.unwrapImageKey(media.keyWrap!!), nonce).apply {
            updateAAD(GCM_AAD.encodeToByteArray())
        }

        return aesDecrypt(cipher).buffer()
    }

    // Only the first transformed bytes are XORed; the rest of the file is plain
    private fun BufferedSource.decodeXorEnvelope(media: MediaDto): BufferedSource {
        skip(MAGIC_SIZE)
        val transformedBytes = readInt().toLong()
        request(transformedBytes)
        val prefix = readByteArray(minOf(transformedBytes, buffer.size)).xor(media.key!!.decodeBase64Url())

        return SequenceInputStream(prefix.inputStream(), inputStream()).source().buffer()
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get(apiUrl.addPathSegments("catalog/meta").build(), Fetch.XHR)
        .parseAs<FilterData>()
        .genres.getValue("ALL")
        .map(GenreDto::name)
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<String>>()

        return FilterList(
            buildList {
                add(SortFilter())
                add(FormatFilter())
                add(StatusFilter())
                if (genres != null) {
                    add(GenreFilter(genres))
                    add(AllGenresFilter())
                }
            },
        )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_LOCKED_PREF
            title = "Mostrar capítulos bloqueados"
            summary = "Lista com 🔒 os capítulos de acesso antecipado ou premium, que não abrem sem acesso no site"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val PAGE_SIZE = 24
        private const val PAGE_HOST = "onereader.invalid"
        private const val SHOW_LOCKED_PREF = "show_locked_chapters"
        private const val ATTEMPTS = 3
        private const val MEDIA_ACCEPT = "application/vnd.onereader.media+json"
        private const val IMAGE_ACCEPT = "application/octet-stream,*/*;q=0.5"
        private const val MAGIC_SIZE = 4L
        private const val GCM_AAD = "oneReader-orx4-v1"
        private val MANGA_PATHS = setOf("obra", "leitor")
        private val MEDIA_REFRESH_CODES = setOf(
            "READER_GRANT_INVALID",
            "READER_PROOF_EXPIRED",
            "READER_PAGE_WINDOW_ADVANCE",
        )
    }
}
