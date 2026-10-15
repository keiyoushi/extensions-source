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
import keiyoushi.source.KeiSource
import keiyoushi.utils.WebViewTimeoutException
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes
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
        val chapterId = "$baseUrl${chapter.url}".toHttpUrl().pathSegments[0]
        val dto = client.get("$apiBaseUrl/chapters/$chapterId").parseAs<ChapterDto>()
        if (dto.isLocked) return emptyList()
        if (dto.deliverySystem != "secure") {
            return dto.pages.orEmpty().map { Page(it.index, imageUrl = it.url) }
        }
        return captureSecurePages(getChapterUrl(chapter)).mapIndexed { index, data ->
            val imageUrl = HttpUrl.Builder().scheme("https").host(PAGE_HOST)
                .addPathSegment(index.toString())
                .fragment(data)
                .build()
            Page(index, imageUrl = imageUrl.toString())
        }
    }

    // Secure chapters decoded by the site's reader
    private suspend fun captureSecurePages(chapterUrl: String): List<String> {
        val bridge = randomName()
        val channel = randomName()
        val workerHook = "(${asset("worker_hook.js")})(${channel.toJsonString()});\n"
        val bootstrap = "(${asset("reader_capture.js")})(window.$bridge, ${channel.toJsonString()}, ${workerHook.toJsonString()});"

        val siteHost = baseUrl.toHttpUrl().host
        val count = AtomicInteger()
        val challengeSeen = AtomicBoolean()
        return try {
            runWebView(timeout = 2.minutes) {
                domStorageEnabled = true
                userAgent = headers["User-Agent"]!!

                val pages = ConcurrentHashMap<Int, String>()

                interceptRequest { request ->
                    val host = request.url.host
                    when {
                        host == "challenges.cloudflare.com" -> {
                            challengeSeen.set(true)
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
                            pages[index] = data.substringAfter("base64,")
                            if (pages.size == count.get()) resolve(List(pages.size) { pages.getValue(it) })
                        }
                        "error" -> reject(Exception(data))
                    }
                }
                injectAtDocumentStart(bootstrap, setOf(baseUrl))

                loadUrl(chapterUrl)
            }
        } catch (e: WebViewTimeoutException) {
            if (challengeSeen.get() && count.get() == 0) throw Exception("Turnstile challenge not passed, open the chapter in WebView and try again")
            throw e
        }
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor { chain ->
        val request = chain.request()
        if (request.url.host != PAGE_HOST) return@addInterceptor chain.proceed(request)
        val data = request.url.fragment ?: throw IOException("Page data missing, clear chapter cache and try again")
        val image = Base64.decode(data, Base64.DEFAULT)
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(image.toResponseBody())
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
        private const val PAGE_HOST = "golgebahcesi-pages.local"
    }
}
