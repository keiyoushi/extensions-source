package eu.kanade.tachiyomi.extension.tr.golgebahcesi

import android.util.Base64
import android.webkit.WebResourceResponse
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.applicationContext
import keiyoushi.utils.getTurnstileToken
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.source
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Source
abstract class GolgeBahcesi : KeiSource() {

    private val apiBaseUrl get() = "https://api.golgebahcesi.com/api"

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", SortFilter.POPULAR)

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", SortFilter.LATEST)

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = url.pathSegments.getOrNull(1) ?: return null
        return fetchMangaUpdate(
            SManga.create().apply { this.url = slug },
            emptyList(),
            true,
            false,
        ).manga
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiBaseUrl/series".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "24")
            if (query.isNotBlank()) addQueryParameter("search", query)

            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> addQueryParameter("sort", filter.toUriPart())
                    is StatusFilter -> filter.toUriPart().takeIf { it.isNotBlank() }?.let { addQueryParameter("status", it) }
                    is TypeFilter -> filter.toUriPart().takeIf { it.isNotBlank() }?.let { addQueryParameter("type", it) }
                    is GenreFilter -> filter.toUriPart().takeIf { it.isNotBlank() }?.let { addQueryParameter("genre", it) }
                    is MinChaptersFilter -> filter.state.takeIf { it.isNotBlank() }?.let { addQueryParameter("minChapters", it) }
                    else -> {}
                }
            }
        }.build()

        val result = client.get(url).parseAs<SeriesListResponse>()
        val mangas = result.data.map { it.toSManga() }
        val hasNextPage = result.pagination?.let { it.currentPage < it.totalPages } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga) = client.get(
        "$apiBaseUrl/recommendations/series/${manga.url}/similar",
    ).parseAs<Recommended>().recommendations.map { it.toSManga() }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val updatedManga = async {
            if (fetchDetails) {
                client.get("$apiBaseUrl/series/${manga.url}").parseAs<SeriesDto>().toSManga()
            } else {
                manga
            }
        }
        val updatedChapters = async {
            if (fetchChapters) {
                client.get("$apiBaseUrl/series/${manga.url}/chapters").parseAs<List<ChapterDto>>().map { chapter ->
                    SChapter.create().apply {
                        url = "/${chapter.id}/${chapter.seriesSlug}/${chapter.slug}"
                        name = chapter.title
                        chapter_number = chapter.number
                        date_upload = Instant.tryParse(chapter.releaseDate ?: chapter.createdAt)
                    }
                }
            } else {
                chapters
            }
        }

        SMangaUpdate(updatedManga.await(), updatedChapters.await())
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val segments = "$baseUrl${chapter.url}".toHttpUrl().pathSegments
        return "$baseUrl/manga/${segments[1]}/bolum/${segments[2]}"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (chapterId, series, chapterSlug) = "$baseUrl${chapter.url}".toHttpUrl().encodedPathSegments
        val dto = client.get("$apiBaseUrl/chapters/$chapterId").parseAs<ChapterDto>()
        if (dto.isLocked) return emptyList()
        if (dto.deliverySystem != "secure") {
            return dto.pages.orEmpty().map { Page(it.index, imageUrl = it.url) }
        }
        return dto.pages.orEmpty().indices.map {
            val imageUrl = HttpUrl.Builder().scheme("https").host(CACHE_HOST)
                .addEncodedPathSegments("$series/$chapterSlug/$it")
                .build()
            Page(it, imageUrl = imageUrl.toString())
        }
    }

    private val captureMutex = Mutex()

    // Replay a chapter's failed capture for 30 s
    private var lastFailure: CaptureFailure? = null

    private class CaptureFailure(val filePrefix: String, val error: Exception, val time: Long)

    private suspend fun captureChapter(chapterUrl: String, filePrefix: String) {
        sweepPageCache()
        try {
            captureSecurePages(chapterUrl, filePrefix)
        } catch (e: ChallengeRequired) {
            val token = getTurnstileToken(url = chapterUrl, siteKey = e.siteKey)
            client.post("$apiBaseUrl/chapters/challenge", body = ChallengeBody(token, "turnstile").toJsonRequestBody()).close()
            captureSecurePages(chapterUrl, filePrefix)
        }
    }

    // Decode secure chapters in the site's reader, with a worker hook returning each image
    private suspend fun captureSecurePages(chapterUrl: String, filePrefix: String) {
        val bridge = randomName()
        val channel = randomName()
        val workerHook = "(${asset("worker_hook.js")})(${channel.toJsonString()});\n"
        val bootstrap = "(${asset("reader_capture.js")})(window.$bridge, ${channel.toJsonString()}, ${workerHook.toJsonString()});"

        val siteHost = baseUrl.toHttpUrl().host
        val writer = Executors.newSingleThreadExecutor()
        try {
            runWebView<Unit>(timeout = 2.minutes) {
                domStorageEnabled = true
                userAgent = headers["User-Agent"]!!

                val count = AtomicInteger()
                val received = HashSet<Int>()
                val challenge = AtomicReference<Pair<String, Long>>()

                interceptRequest { request ->
                    val host = request.url.host
                    when {
                        host == "challenges.cloudflare.com" -> {
                            TURNSTILE_SITE_KEY.find(request.url.toString())?.let {
                                challenge.compareAndSet(null, it.value to System.currentTimeMillis())
                            }
                            null
                        }
                        host == null || host == siteHost || host.endsWith(".$siteHost") -> null
                        else -> WebResourceResponse("text/plain", null, 403, "Blocked", null, ByteArrayInputStream(ByteArray(0)))
                    }
                }
                webMessageBridge(bridge, setOf(baseUrl)) { message ->
                    val data = message.substringAfter(':')
                    when (message.substringBefore(':')) {
                        "count" -> count.set(data.toInt())
                        "page" -> {
                            val index = data.substringBefore(',').toInt()
                            require(index in 0 until count.get())
                            writer.execute {
                                try {
                                    val bytes = Base64.decode(data.substringAfter("base64,"), Base64.DEFAULT)
                                    val file = File(applicationContext.cacheDir, "$filePrefix$index")
                                    check(File(file.path + ".tmp").apply { writeBytes(bytes) }.renameTo(file))
                                    received += index
                                    if (received.size == count.get()) resolve(Unit)
                                } catch (e: Exception) {
                                    reject(e)
                                }
                            }
                        }
                        "error" -> reject(Exception(data))
                    }
                }
                injectAtDocumentStart(bootstrap, setOf(baseUrl))
                poll(1.seconds) {
                    val (siteKey, seenAt) = challenge.get() ?: return@poll
                    if (count.get() == 0 && System.currentTimeMillis() - seenAt > 10.seconds.inWholeMilliseconds) {
                        reject(ChallengeRequired(siteKey))
                    }
                }

                loadUrl(chapterUrl)
            }
        } finally {
            writer.shutdownNow()
        }
    }

    private class ChallengeRequired(val siteKey: String) : Exception("Turnstile required")

    // Keep captured pages for a day
    private fun sweepPageCache() {
        val cutoff = System.currentTimeMillis() - 1.days.inWholeMilliseconds
        applicationContext.cacheDir.listFiles()?.forEach {
            if (it.name.startsWith(CACHE_FILE_PREFIX) && it.lastModified() < cutoff) it.delete()
        }
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor { chain ->
        val request = chain.request()
        if (request.url.host != CACHE_HOST) return@addInterceptor chain.proceed(request)
        val (series, chapter, index) = request.url.encodedPathSegments
        val filePrefix = "$CACHE_FILE_PREFIX${series}_${chapter}_"
        val file = File(applicationContext.cacheDir, "$filePrefix$index")
        if (!file.exists()) {
            val call = chain.call()
            try {
                runBlocking {
                    val capture = async {
                        captureMutex.withLock {
                            if (file.exists()) return@withLock
                            lastFailure?.takeIf { it.filePrefix == filePrefix && System.currentTimeMillis() - it.time < 30.seconds.inWholeMilliseconds }
                                ?.let { throw it.error }
                            try {
                                captureChapter("$baseUrl/manga/$series/bolum/$chapter", filePrefix)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                lastFailure = CaptureFailure(filePrefix, e, System.currentTimeMillis())
                                throw e
                            }
                        }
                    }
                    val watcher = launch {
                        while (!call.isCanceled()) delay(500)
                        capture.cancel()
                    }
                    try {
                        capture.await()
                    } finally {
                        watcher.cancel()
                    }
                }
            } catch (e: Throwable) {
                throw e as? IOException ?: IOException(e.message, e)
            }
        }
        if (!file.exists()) throw IOException("Page ${index.toInt() + 1} was not captured")
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(file.source().buffer().asResponseBody())
            .build()
    }

    private fun asset(name: String) = javaClass.getResource("/assets/$name")!!.readText()

    private fun randomName() = List(12) { ('a'..'z').random() }.joinToString("")

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData() = client.get("$apiBaseUrl/genres")
        .parseAs<List<Genre>>()
        .toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        listOf(
            SortFilter(),
            StatusFilter(),
            TypeFilter(),
            MinChaptersFilter(),
        ) + listOfNotNull(
            data?.parseAs<List<Genre>>()?.map { it.name to it.slug }
                ?.takeIf { it.isNotEmpty() }
                ?.let(::GenreFilter),
        ),
    )

    companion object {
        private const val CACHE_HOST = "golgebahcesi-pages.local"
        private const val CACHE_FILE_PREFIX = "golgebahcesi-"
        private val TURNSTILE_SITE_KEY = Regex("""0x[0-9A-Za-z_-]{20,}""")
    }
}
