package eu.kanade.tachiyomi.extension.fr.lesporoiniens

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.coroutines.cancellation.CancellationException

@Source
abstract class LesPoroiniens : KeiSource() {

    companion object {
        private const val SERIES_DATA_SELECTOR = "#series-data-placeholder"
        private const val READER_DATA_SELECTOR = "#reader-data-placeholder"
        private const val CACHE_TTL_MS = 10 * 60 * 1000L
    }

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor { chain ->
        val response = chain.proceed(chain.request())
        if (response.code == 403 && response.request.url.encodedPath.startsWith("/data/series/")) {
            val bodyString = response.peekBody(16 * 1024).string()
            if (bodyString.contains("Accès refusé", true) || bodyString.contains("Erreur 404", true)) {
                response.close()
                return@addInterceptor response.newBuilder()
                    .code(404)
                    .message("Not Found")
                    .build()
            }
        }
        response
    }

    @Volatile
    private var catalogueCache: List<SeriesData>? = null

    @Volatile
    private var catalogueTimestamp = 0L

    private val cacheMutex = Mutex()

    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)
        return fetchCatalogue().toMangasPage()
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return getPopularManga(page)
        if (page > 1) return MangasPage(emptyList(), false)
        return catalogueSearch(trimmedQuery)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val direct = try {
            val document = client.get(url).asJsoup()
            document.selectFirst(SERIES_DATA_SELECTOR)?.html()?.parseAs<SeriesData>()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (direct != null) return direct.toDetailedSManga()
        val slug = url.pathSegments.firstOrNull { it.isNotEmpty() } ?: return null
        val wanted = slugAlphanumeric(slug)
        val match = fetchCatalogue().firstOrNull {
            slugAlphanumeric(toSlug(it.title)) == wanted
        } ?: return null
        return match.toDetailedSManga()
    }

    private fun slugAlphanumeric(slug: String): String = slug.lowercase().filter { it.isLetterOrDigit() }

    private fun List<SeriesData>.toMangasPage(): MangasPage = MangasPage(map { it.toSManga() }, false)

    private suspend fun catalogueSearch(query: String): MangasPage {
        val matches = fetchCatalogue().filter {
            it.title.contains(query, ignoreCase = true) ||
                it.author?.contains(query, ignoreCase = true) == true ||
                it.artist?.contains(query, ignoreCase = true) == true ||
                it.alternativeTitles?.any { title -> title.contains(query, ignoreCase = true) } == true
        }
        return matches.toMangasPage()
    }

    private suspend fun fetchCatalogue(): List<SeriesData> {
        getFreshCatalogue()?.let { return it }
        return cacheMutex.withLock {
            getFreshCatalogue()?.let { return it }
            val config = client.get("$baseUrl/data/config.json").parseAs<ConfigResponse>()
            val catalogue = fetchSeriesFiles(config.localSeriesFiles)
            catalogueCache = catalogue
            catalogueTimestamp = System.currentTimeMillis()
            catalogue
        }
    }

    private fun getFreshCatalogue(): List<SeriesData>? {
        val cached = catalogueCache
        return if (cached != null && isFresh(catalogueTimestamp)) cached else null
    }

    private fun isFresh(timestamp: Long): Boolean = System.currentTimeMillis() - timestamp < CACHE_TTL_MS

    private suspend fun fetchSeriesFiles(fileNames: List<String>): List<SeriesData> = coroutineScope {
        fileNames.map { fileName ->
            async {
                try {
                    client.get("$baseUrl/data/series/$fileName").parseAs<SeriesData>()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val seriesData = document.selectFirst(SERIES_DATA_SELECTOR)!!.html().parseAs<SeriesData>()
        val updatedManga = if (fetchDetails) {
            seriesData.toDetailedSManga()
        } else {
            manga
        }
        val chapterList = if (fetchChapters) buildChapterList(seriesData) else chapters
        return SMangaUpdate(updatedManga, chapterList)
    }

    private fun buildChapterList(seriesData: SeriesData): List<SChapter> {
        val chapters = seriesData.chapters ?: return emptyList()
        val chapterList = mutableListOf<SChapter>()
        val multipleChapters = chapters.size > 1
        val mangaUrl = seriesData.mangaUrl

        for ((chapterNumber, chapterData) in chapters) {
            if (chapterData.licencied) continue

            val title = chapterData.title ?: ""
            val volumeNumber = chapterData.volume ?: ""

            val baseName = if (multipleChapters) {
                buildString {
                    if (volumeNumber.isNotBlank()) append("Vol. $volumeNumber ")
                    append("Ch. $chapterNumber")
                    if (title.isNotBlank()) append(" – $title")
                }
            } else {
                if (title.isNotBlank()) "One Shot – $title" else "One Shot"
            }

            val chapter = SChapter.create().apply {
                name = baseName
                url = "$mangaUrl/$chapterNumber"
                chapter_number = chapterNumber.toFloatOrNull() ?: -1f
                date_upload = chapterData.lastUpdated * 1000L
            }
            chapterList.add(chapter)
        }

        return chapterList.sortedByDescending { it.chapter_number }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val chapterNumber = chapter.url.trimEnd('/').substringAfterLast('/')
        val jsonData = document.selectFirst(READER_DATA_SELECTOR)!!.html()

        val readerData = jsonData.parseAs<LocalReaderData>()
        val chapterData = readerData.series.chapters?.get(chapterNumber)
            ?: throw NoSuchElementException("Chapter data not found for chapter $chapterNumber")

        val chapterUrl = chapterData.groups?.values?.firstOrNull()
            ?: throw NoSuchElementException("Chapter URL not found for chapter $chapterNumber")

        val imageUrls = if (chapterUrl.contains("imgchest")) {
            val chapterId = chapterUrl.substringAfterLast("/")
            client.get("$baseUrl/api/imgchest-chapter-pages?id=$chapterId").parseAs<List<PageData>>()
                .map { it.link }
        } else {
            client.get("$baseUrl$chapterUrl").parseAs<List<String>>()
        }
        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }
}
