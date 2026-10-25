package eu.kanade.tachiyomi.extension.vi.leesincomic

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
import keiyoushi.utils.getArrayOrNull
import keiyoushi.utils.obj
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import keiyoushi.utils.tryParseDate
import keiyoushi.utils.tryParseDateTime
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Source
abstract class LeesinComic : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(3)
        // get_comics is ~6 MB and the server can take over a minute to deliver it
        // the default read timeout kills the request before the body arrives.
        readTimeout(2, TimeUnit.MINUTES)
    }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = this
        .set("X-API-KEY", "Leesin_Secret_MySQL_Key_2026")
        .set("x-api-key", "Leesin_Secret_MySQL_Key_2026")
        .removeAll("Origin")

    // CDN. tachserver.online drops connections; identical paths live on tachserver.site.
    private fun String.fixImageHost(): String = replace("://tachserver.online/", "://tachserver.site/")

    // ============================== Popular ===============================

    // get_comics serves the whole ~6 MB catalog per call
    // memoize briefly and share the in-flight request so browse/search/filter don't refetch it.
    private val catalogMutex = Mutex()
    private var catalogCache: Pair<Long, List<CatalogComicDto>>? = null
    private var catalogJob: Deferred<List<CatalogComicDto>>? = null

    private suspend fun fetchCatalog(): List<CatalogComicDto> {
        catalogCache?.takeIf { Clock.System.now().toEpochMilliseconds() - it.first < catalogTtl }
            ?.let { return it.second }

        return coroutineScope {
            catalogMutex.withLock {
                catalogCache?.takeIf { Clock.System.now().toEpochMilliseconds() - it.first < catalogTtl }
                    ?.let { return@coroutineScope it.second }

                val job = catalogJob ?: async {
                    client.get("$baseUrl/api.php?action=get_comics")
                        .parseAs<ComicsResponse>().comics
                }.also { catalogJob = it }

                try {
                    job.await().also { comics ->
                        catalogCache = Clock.System.now().toEpochMilliseconds() to comics
                        catalogJob = null
                    }
                } catch (e: Exception) {
                    catalogJob = null
                    throw e
                }
            }
        }
    }

    private val catalogTtl = 5.minutes.inWholeMilliseconds

    private fun List<CatalogComicDto>.toMangasPage(): MangasPage = MangasPage(map { it.toSManga() }, hasNextPage = false)

    private fun CatalogComicDto.toSManga(): SManga = SManga.create().apply {
        setUrlWithoutDomain("/truyen/$slug")
        title = this@toSManga.title
        thumbnail_url = coverImage?.fixImageHost()
    }

    override suspend fun getPopularManga(page: Int): MangasPage = fetchCatalog()
        .sortedByDescending { it.views }
        .toMangasPage()

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchCatalog()
        .sortedByDescending { it.updatedAt.parseApiDate() }
        .toMangasPage()

    // ============================== Search ================================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        var comics = fetchCatalog()

        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            comics = comics.filter { c ->
                c.title.lowercase().contains(q) ||
                    c.slug.lowercase().contains(q) ||
                    c.otherNames?.lowercase()?.contains(q) == true
            }
        }

        val genre = filters.firstInstanceOrNull<GenreFilter>()?.selected
        if (!genre.isNullOrEmpty()) {
            comics = comics.filter { c -> c.genres.any { it.equals(genre, ignoreCase = true) } }
        }

        when (filters.firstInstanceOrNull<StatusFilter>()?.state) {
            1 -> comics = comics.filter { it.isOngoing() }
            2 -> comics = comics.filter { it.isCompleted() }
        }

        comics = when (filters.firstInstanceOrNull<SortFilter>()?.state ?: 0) {
            1 -> comics.sortedByDescending { it.likes }
            2 -> comics.sortedByDescending { it.rating }
            3 -> comics.sortedByDescending { it.updatedAt.parseApiDate() }
            else -> comics.sortedByDescending { it.views }
        }

        return comics.toMangasPage()
    }

    private fun CatalogComicDto.isOngoing(): Boolean = status.isOngoing()

    private fun CatalogComicDto.isCompleted(): Boolean = status.isCompleted()

    private fun ComicDto.isOngoing(): Boolean = status.isOngoing()

    private fun ComicDto.isCompleted(): Boolean = status.isCompleted()

    private fun String?.isOngoing(): Boolean = equals("Đang tiến hành", ignoreCase = true)

    private fun String?.isCompleted(): Boolean = equals("Hoàn thành", ignoreCase = true) ||
        equals("Đã hoàn thành", ignoreCase = true)

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        if (segments.isEmpty() || segments[0] != "truyen" || segments.size < 2) return null
        val slug = segments[1]
        if (slug.isBlank()) return null

        return fetchMangaUpdate(
            SManga.create().apply { setUrlWithoutDomain("/truyen/$slug") },
            emptyList(),
            fetchDetails = true,
            fetchChapters = false,
        ).manga
    }

    // ============================== Details ===============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.slugFromUrl()
        val comic = client.get("$baseUrl/api.php?action=get_comic&id=$slug")
            .parseAs<ComicResponse>().comic

        return SMangaUpdate(
            manga = manga.apply {
                title = comic.title
                thumbnail_url = comic.coverImage?.fixImageHost() ?: thumbnail_url
                genre = comic.genres.joinToString()
                author = comic.authors.joinToString().takeIf { it.isNotBlank() }
                description = buildString {
                    comic.altTitles.takeIf { it.isNotEmpty() }
                        ?.let { append("Tên khác: ").append(it.joinToString(" / ")).append("\n\n") }
                    comic.summary?.takeIf { it.isNotBlank() }?.let { append(it) }
                }.trim()
                status = when {
                    comic.isOngoing() -> SManga.ONGOING
                    comic.isCompleted() -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
            },
            chapters = comic.chapters
                .sortedByDescending { it.chapterNumber ?: 0.0 }
                .map { it.toSChapter(comic.slug) },
        )
    }

    private fun ChapterDto.toSChapter(comicSlug: String): SChapter = SChapter.create().apply {
        setUrlWithoutDomain("/truyen/$comicSlug/chap-${chapterNumber.formatChapterNum()}")
        name = title ?: "Chương ${chapterNumber.formatChapterNum()}"
        chapter_number = chapterNumber?.toFloat() ?: -1f
        date_upload = (createdAt ?: updatedAt).parseApiDate()
    }

    private fun Double?.formatChapterNum(): String {
        val d = this ?: return "0"
        return if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
    }

    private fun String.slugFromUrl(): String = removeSuffix("/").substringAfterLast("/")

    private val localDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
    private val localDate = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    private val siteZone: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

    private fun String?.parseApiDate(): Long = Instant.tryParse(this).takeIf { it > 0L }
        ?: localDateTime.tryParseDateTime(this, siteZone).takeIf { it > 0L }
        ?: localDate.tryParseDate(this, siteZone)

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val segments = chapter.url.removePrefix("/").split("/")
        val slug = segments.getOrNull(1)
            ?: throw Exception("Invalid chapter url: ${chapter.url}")
        val chapterNumber = segments.getOrNull(2)
            ?.removePrefix("chap-")
            ?: throw Exception("Invalid chapter url: ${chapter.url}")

        val apiUrl = "$baseUrl/api.php?action=get_chapter".toHttpUrl().newBuilder()
            .addQueryParameter("comicSlug", slug)
            .addQueryParameter("chapterNumber", chapterNumber)
            .build()

        val images = client.get(apiUrl).parseAs<ChapterResponse>().chapter.images
        return images.mapIndexed { index, url ->
            Page(index, imageUrl = url.fixImageHost())
        }
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val root = client.get("$baseUrl/api.php?action=get_comics").parseAs<JsonElement>()
        val genres = root.obj.getArrayOrNull("comics").orEmpty()
            .flatMap { (it as? JsonObject)?.getArrayOrNull("genres").orEmpty() }
            .mapNotNull { it.stringOrNull?.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sorted()
        return FilterData(genres = genres).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = buildFilterList(data?.parseAs<FilterData>())
}
