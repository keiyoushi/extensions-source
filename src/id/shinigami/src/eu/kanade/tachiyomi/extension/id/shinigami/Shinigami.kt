package eu.kanade.tachiyomi.extension.id.shinigami

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
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.time.Instant

@Source
abstract class Shinigami : KeiSource() {
    private val apiUrl = "https://api.shngm.io"

    private val apiHeaders: Headers
        get() = headersBuilder()
            .add("Accept", "application/json")
            .add("DNT", "1")
            .add("Sec-GPC", "1")
            .build()

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    // ====================== Popular ======================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/v1/manga/list".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("page_size", "30")
            .addQueryParameter("sort", "popularity")
            .build()

        val response = client.get(url, apiHeaders)
        return parseMangaList(response)
    }

    // ====================== Latest ======================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/v1/manga/list".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("page_size", "30")
            .addQueryParameter("sort", "latest")
            .build()

        val response = client.get(url, apiHeaders)
        return parseMangaList(response)
    }

    // ====================== Search ======================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/v1/manga/list".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("page_size", "30")

        if (query.isNotEmpty()) {
            url.addQueryParameter("q", query)
        }

        filters.filterIsInstance<UriFilter>().forEach {
            it.addToUri(url)
        }

        val response = client.get(url.build(), apiHeaders)
        return parseMangaList(response)
    }

    private fun parseMangaList(response: Response): MangasPage {
        val rootObject = response.parseAs<ShinigamiBrowseDto>()
        val projectList = rootObject.data.map(::popularMangaFromObject)
        val hasNextPage = rootObject.meta.totalPage?.let { rootObject.meta.page < it } ?: false
        return MangasPage(projectList, hasNextPage)
    }

    private fun popularMangaFromObject(obj: ShinigamiBrowseDataDto): SManga = SManga.create().apply {
        title = obj.title.orEmpty()
        thumbnail_url = obj.thumbnail
        url = obj.mangaId.orEmpty()
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(),
        SortOrderFilter(),
        StatusFilter(),
        FormatFilter(),
        TypeFilter(),
        GenreFilter(),
    )

    // ====================== Manga Details & Chapters ======================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != "shinigami.asia" && !url.host.endsWith(".shinigami.asia")) return null
        val firstSegment = url.pathSegments.firstOrNull()
        if (firstSegment != "series") return null
        val id = url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null

        val manga = SManga.create().apply {
            this.url = id
        }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        if (manga.url.startsWith("/series/")) {
            throw Exception("Migrate dari $name ke $name (ekstensi yang sama)")
        }

        val detailsDeferred = if (fetchDetails) {
            async { fetchMangaDetails(manga) }
        } else {
            null
        }

        val chaptersDeferred = if (fetchChapters) {
            async { fetchChapterList(manga) }
        } else {
            null
        }

        val updatedManga = detailsDeferred?.await() ?: manga
        val updatedChapters = chaptersDeferred?.await() ?: chapters

        SMangaUpdate(updatedManga, updatedChapters)
    }

    private suspend fun fetchMangaDetails(manga: SManga): SManga {
        val response = client.get("$apiUrl/v1/manga/detail/${manga.url}", apiHeaders)
        val mangaDetailsResponse = response.parseAs<ShinigamiMangaDetailDto>()
        val mangaDetails = mangaDetailsResponse.data

        return manga.apply {
            mangaDetails.title?.takeIf { it.isNotBlank() }?.let { title = it }
            (mangaDetails.coverPortraitUrl ?: mangaDetails.coverImageUrl)?.takeIf { it.isNotBlank() }?.let {
                thumbnail_url = it
            }
            author = mangaDetails.taxonomy["Author"]?.joinToString { it.name }.orEmpty()
            artist = mangaDetails.taxonomy["Artist"]?.joinToString { it.name }.orEmpty()
            status = mangaDetails.status.toStatus()
            description = buildString {
                append(mangaDetails.description)
                mangaDetails.altTitle?.takeIf { it.isNotBlank() }?.let {
                    if (isNotEmpty()) append("\n\n")
                    append("Alternative Title: ").append(it)
                }
            }

            val genres = mangaDetails.taxonomy["Genre"]?.joinToString { it.name }.orEmpty()
            val type = mangaDetails.taxonomy["Format"]?.joinToString { it.name }.orEmpty()
            genre = listOf(genres, type).filter { it.isNotBlank() }.joinToString()
        }
    }

    private fun Int.toStatus(): Int = when (this) {
        1 -> SManga.ONGOING
        2 -> SManga.COMPLETED
        3 -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        val response = client.get("$apiUrl/v1/chapter/${manga.url}/list?page_size=3000", apiHeaders)
        val result = response.parseAs<ShinigamiChapterListDto>()
        return result.chapterList.map(::chapterFromObject)
    }

    private fun chapterFromObject(obj: ShinigamiChapterListDataDto): SChapter = SChapter.create().apply {
        date_upload = Instant.tryParse(obj.date)
        name = "Chapter ${obj.name.toString().removeSuffix(".0")} ${obj.title}".trim()
        chapter_number = obj.name.toFloat()
        url = obj.chapterId
    }

    // ====================== Page List ======================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.url.startsWith("/series/")) {
            throw Exception("Migrate dari $name ke $name (ekstensi yang sama)")
        }

        val response = client.get("$apiUrl/v1/chapter/detail/${chapter.url}", apiHeaders)
        val result = response.parseAs<ShinigamiPageListDto>()

        return result.pageList.chapterPage.pages.mapIndexed { index, imageName ->
            Page(index, imageUrl = "${result.pageList.baseUrl}${result.pageList.chapterPage.path}$imageName")
        }
    }

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .add("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .add("DNT", "1")
            .add("sec-fetch-dest", "empty")
            .add("Sec-GPC", "1")
            .build()

        return Request.Builder()
            .url(page.imageUrl!!)
            .headers(imageHeaders)
            .get()
            .build()
    }
}
