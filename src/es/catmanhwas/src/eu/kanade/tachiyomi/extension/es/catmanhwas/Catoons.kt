package eu.kanade.tachiyomi.extension.es.catmanhwas

import android.util.Base64
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Catoons : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 3, period = 1.seconds)

    override suspend fun getPopularManga(page: Int): MangasPage = fetchBrowsePage(page, "", FilterList(OrderFilter(listOf("" to "popular"))))

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        ensureLatestChunk()

        val url = "$baseUrl/_app/remote/$latestChunk/getLatestChapters".toHttpUrl().newBuilder()
            .addQueryParameter("payload", "[$page]".toBase64())
            .build()

        val result = client.get(url).parseAs<SvelteResultDto>().getResult()
        val data = decodeSvelte(result.jsonArray).parseAs<LatestChaptersDto>()

        val mangas = data.data.map { it.toSManga() }.distinctBy { it.url }
        return MangasPage(mangas, data.pagination.hasNextPage())
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = fetchBrowsePage(page, query, filters)

    private suspend fun fetchBrowsePage(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/series/__data.json".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        filters.firstInstanceOrNull<GenreFilter>()?.let { url.addQueryParameter("genre", it.selected) }
        filters.firstInstanceOrNull<OrderFilter>()?.let { url.addQueryParameter("sort", it.selected) }

        if (query.isNotBlank()) {
            url.addQueryParameter("search", query)
        }

        url.addQueryParameter("x-sveltekit-invalidated", "001")

        val dataNode = client.get(url.build()).parseAs<SvelteDataDto>().getDataNode()
        val data = decodeSvelte(dataNode).parseAs<BrowseDto>()
        return MangasPage(data.series.map { it.toSManga() }, data.hasNextPage())
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/series/${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/series/${chapter.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.getOrNull(0) != "series") return null

        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null

        return SManga.create().apply {
            this.url = slug
            title = slug
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaDeferred = async { if (fetchDetails) fetchMangaDetails(manga) else manga }
        val chaptersDeferred = async { if (fetchChapters) fetchChapterList(manga) else chapters }
        SMangaUpdate(mangaDeferred.await(), chaptersDeferred.await())
    }

    private suspend fun fetchMangaDetails(manga: SManga): SManga = coroutineScope {
        // Independent of the remote chunk discovery, so start it before the WebView run.
        val seriesPageDeferred = async { getSeriesPage(manga.url) }

        ensureSeriesChunks(getMangaUrl(manga))
        val details = getDetailsFromApi(manga.url)
        val seriesPage = seriesPageDeferred.await()

        seriesPage.toSManga().apply {
            status = details.getStatus()
            genre = details.getGenres()
        }
    }

    private suspend fun getSeriesPage(slug: String): SeriesPageDto {
        val url = "$baseUrl/series/$slug/__data.json".toHttpUrl().newBuilder()
            .addQueryParameter("x-sveltekit-invalidated", "001")
            .build()

        val dataNode = client.get(url).parseAs<SvelteDataDto>().getDataNode()
        return decodeSvelte(dataNode).parseAs<SeriesPageDto>()
    }

    private suspend fun getDetailsFromApi(slug: String): DetailsDto {
        val url = "$baseUrl/_app/remote/$detailsChunk/getSerieDetails".toHttpUrl().newBuilder()
            .addQueryParameter("payload", """["$slug"]""".toBase64())
            .build()

        val result = client.get(url).parseAs<SvelteResultDto>().getResult()
        return decodeSvelte(result.jsonArray).parseAs<DetailsDto>()
    }

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        ensureSeriesChunks(getMangaUrl(manga))

        var chapterData = getChaptersPage(manga.url, 1)
        val chapterList = chapterData.data.map { it.toSChapter(manga.url) }.toMutableList()

        while (chapterData.pagination.hasNextPage()) {
            chapterData = getChaptersPage(manga.url, chapterData.pagination.currentPage + 1)
            chapterList.addAll(chapterData.data.map { it.toSChapter(manga.url) })
        }

        return chapterList
    }

    private suspend fun getChaptersPage(slug: String, page: Int): ChapterDataDto {
        val url = "$baseUrl/_app/remote/$chaptersChunk/getChapters".toHttpUrl().newBuilder()
            .addQueryParameter("payload", getChapterPayload(slug, page))
            .build()

        val result = client.get(url).parseAs<SvelteResultDto>().getResult()
        return decodeSvelte(result.jsonArray).parseAs<ChapterDataDto>()
    }

    private fun getChapterPayload(slug: String, page: Int): String {
        val payload = """[["__skrao",1],{"page":2,"slug":3,"perPage":4},$page,"$slug",$CHAPTERS_PER_PAGE]"""
        return payload.toBase64()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "${getChapterUrl(chapter)}/__data.json".toHttpUrl().newBuilder()
            .addQueryParameter("x-sveltekit-invalidated", "001")
            .build()

        val dataNode = client.get(url).parseAs<SvelteDataDto>().getDataNode()
        return decodeSvelte(dataNode).parseAs<ChapterPageDto>().toPages()
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    @Volatile
    private var detailsChunk: String? = null

    @Volatile
    private var chaptersChunk: String? = null

    @Volatile
    private var latestChunk: String? = null

    private val chunksMutex = Mutex()

    private suspend fun ensureSeriesChunks(seriesUrl: String) {
        if (detailsChunk != null && chaptersChunk != null) return

        chunksMutex.withLock {
            if (detailsChunk != null && chaptersChunk != null) return

            runWebView<Unit> {
                blockImages = true

                interceptRequest { request ->
                    val url = request.url.toString()

                    DETAILS_CHUNK_REGEX.find(url)?.groupValues?.getOrNull(1)?.let { detailsChunk = it }
                    CHAPTERS_CHUNK_REGEX.find(url)?.groupValues?.getOrNull(1)?.let { chaptersChunk = it }

                    if (detailsChunk != null && chaptersChunk != null) {
                        resolve(Unit)
                    }

                    null
                }

                loadUrl(seriesUrl)
            }
        }
    }

    private suspend fun ensureLatestChunk() {
        if (latestChunk != null) return

        chunksMutex.withLock {
            if (latestChunk != null) return

            runWebView<Unit> {
                blockImages = true

                interceptRequest { request ->
                    LATEST_CHUNK_REGEX.find(request.url.toString())?.groupValues?.getOrNull(1)?.let {
                        latestChunk = it
                        resolve(Unit)
                    }

                    null
                }

                loadUrl("$baseUrl/")
            }
        }
    }

    private fun String.toBase64() = Base64.encodeToString(this.toByteArray(), Base64.DEFAULT)

    private fun decodeSvelte(data: JsonArray): JsonElement = resolve(data, data[0])

    private fun dereference(data: JsonArray, index: Int): JsonElement = when (val value = data[index]) {
        is JsonArray, is JsonObject -> resolve(data, value)
        else -> value
    }

    private fun resolveReference(data: JsonArray, element: JsonElement): JsonElement {
        val index = (element as? JsonPrimitive)?.intOrNull

        return if (
            index != null &&
            !element.isString &&
            index in data.indices
        ) {
            dereference(data, index)
        } else {
            resolve(data, element)
        }
    }

    private fun resolve(data: JsonArray, element: JsonElement): JsonElement = when (element) {
        is JsonArray -> JsonArray(element.map { resolveReference(data, it) })
        is JsonObject -> buildJsonObject {
            element.forEach { (key, value) ->
                put(key, resolveReference(data, value))
            }
        }
        else -> element
    }

    companion object {
        private val DETAILS_CHUNK_REGEX = """/_app/remote/([^/]+)/getSerieDetails""".toRegex()
        private val CHAPTERS_CHUNK_REGEX = """/_app/remote/([^/]+)/getChapters""".toRegex()
        private val LATEST_CHUNK_REGEX = """/_app/remote/([^/]+)/getLatestChapters""".toRegex()
        private const val CHAPTERS_PER_PAGE = 100
    }
}
