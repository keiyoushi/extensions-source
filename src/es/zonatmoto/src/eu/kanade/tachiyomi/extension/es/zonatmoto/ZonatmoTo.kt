package eu.kanade.tachiyomi.extension.es.zonatmoto

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import java.time.format.DateTimeFormatter

@Source
abstract class ZonatmoTo : KeiSource() {

    private val baseHost get() = baseUrl.toHttpUrl().host

    private val cdnUrl get() = "https://cdn.$baseHost"
    private val apiUrl get() = "https://$baseHost/wp-api/api".toHttpUrl()
    private val uploadsUrl get() = "https://$baseHost/wp-content/uploads".toHttpUrl()

    // ========================= Popular =========================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = apiUrl.newBuilder()
            .addPathSegments("tops/views/month")
            .addQueryParameter("postType", "any")
            .addQueryParameter("postsPerPage", "50")
            .build()
        val mangas = client.get(url).parseAs<TopViewsResponseDto>().data?.items.orEmpty().mapNotNull {
            it.toSManga()
        }
        return MangasPage(mangas, false)
    }

    // ========================= Latest =========================

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", FilterList())

    // ========================= Search =========================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseHost) return null
        val mangaSlug = url.pathSegments.getOrNull(1) ?: return null

        return fetchMangaUpdate(
            SManga.create().apply { this.url = mangaSlug },
            emptyList(),
            true,
            false,
        ).manga
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = apiUrl.newBuilder().apply {
        addPathSegments("listing/manga")
        addQueryParameter("page", page.toString())

        for (filter in filters) {
            when (filter) {
                is GenreFilter -> filter.state.forEach {
                    if (it.state) addQueryParameter("genres[]", it.value)
                }
                is TypeFilter -> filter.state.forEach {
                    if (it.state) addQueryParameter("type[]", it.value)
                }
                is StatusFilter -> filter.state.forEach {
                    if (it.state) addQueryParameter("status[]", it.value)
                }
                else -> {}
            }
        }
        query.trim().takeIf(String::isNotEmpty)?.let {
            addQueryParameter("search", it)
        } ?: addQueryParameter("orderBy", "post_modified")
    }.let {
        parseSearch(client.get(it.build()))
    }

    private fun parseSearch(response: Response): MangasPage {
        val dto = response.parseAs<ListingResponseDto>()
        val mangas = dto.data
            ?.items
            .orEmpty()
            .mapNotNull { it.toSManga() }

        val hasNextPage = dto.data?.pagination?.hasNext ?: false

        return MangasPage(mangas, hasNextPage)
    }

    // ======================= MangaUpdate =======================

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override val supportRelatedMangasBySearch = true

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ) = coroutineScope {
        val mangaUrl = "$apiUrl/single/manga/${manga.url}"

        val updatedManga = async {
            if (fetchDetails) {
                client.get(mangaUrl).parseAs<SingleMangaResponseDto>()
                    .data
                    ?.toSManga()
                    ?: throw Exception("Unable to parse manga details")
            } else {
                manga
            }
        }

        val updatedChapters = async {
            if (fetchChapters) {
                val chaptersUrl = mangaUrl.toHttpUrl().newBuilder()
                    .addPathSegment("chapters")
                    .addQueryParameter("page", "1")
                    .addQueryParameter("postsPerPage", CHAPTERS_PER_PAGE.toString())
                    .addQueryParameter("order", "asc")
                    .build()

                getChapters(chaptersUrl, manga.url)
            } else {
                chapters
            }
        }

        SMangaUpdate(updatedManga.await(), updatedChapters.await())
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val slugs = chapter.url.split("/", limit = 2)
            .takeIf { it.size == 2 }
            ?: return baseUrl

        return "$baseUrl/manga/${slugs[0]}/${slugs[1]}"
    }

    private suspend fun getChapters(url: HttpUrl, mangaSlug: String): List<SChapter> = coroutineScope {
        val firstPageDto = client.get(url).parseAs<ChapterListResponseDto>()
        val totalPages = firstPageDto.data?.pagination?.totalPages ?: 1

        val nextItems = (2..totalPages).map { page ->
            async {
                val pageUrl = url.newBuilder().setQueryParameter("page", "$page").build()
                client.get(pageUrl)
                    .parseAs<ChapterListResponseDto>()
                    .data?.items.orEmpty()
            }
        }.awaitAll().flatten()

        (firstPageDto.data?.items.orEmpty() + nextItems)
            .distinctBy { it.id }
            .sortedByDescending { it.chapterNumber.toFloatOrNull() ?: -1f }
            .map { it.toSChapter(mangaSlug) }
    }

    // ========================= Pages =========================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = baseUrl.toHttpUrl().resolve(chapter.url)!!

        val pathSegments = chapterUrl.pathSegments
        val mangaSlug = pathSegments[pathSegments.size - 2]
        val chapterSlug = pathSegments[pathSegments.size - 1]

        val chapterData = client.get(
            "$apiUrl/single/manga/$mangaSlug/$chapterSlug",
        ).parseAs<SingleChapterResponseDto>().data?.chapter
            ?: throw Exception("Unable to parse chapter pages")

        return chapterData.images
            .sortedBy(ChapterImageDto::pageNumber)
            .mapIndexed { index, image ->
                Page(
                    index = index,
                    imageUrl = cdnImageUrl(chapterData.jit, image.imageUrl),
                )
            }
    }

    // ========================= Filters =========================

    override fun getFilterList(data: JsonElement?) = FilterList(
        GenreFilter(),
        TypeFilter(),
        StatusFilter(),
    )

    // ========================= Utilities =========================

    private fun cdnImageUrl(jit: String, imageName: String): String = cdnUrl.toHttpUrl().newBuilder()
        .addPathSegment("manga")
        .addPathSegments(jit)
        .addPathSegment(imageName)
        .build()
        .toString()

    private fun MangaDto.toSManga(): SManga? {
        val mangaSlug = slug.trim().takeIf(String::isNotEmpty) ?: return null
        val mangaTitle = title.trim().takeIf(String::isNotEmpty) ?: return null

        return SManga.create().apply {
            url = mangaSlug
            title = mangaTitle
            thumbnail_url = cover.toThumbnailUrl()
            description = overview?.trim().orEmpty().ifEmpty { null }
            genre = this@toSManga.genres
                ?.mapNotNull { id -> GENRES.firstOrNull { it.second == id.toString() }?.first }
                ?.joinToString()
                ?.ifEmpty { null }
            author = this@toSManga.author
                .orEmpty()
                .map { it.name.trim() }
                .filter(String::isNotEmpty)
                .distinct()
                .joinToString()
                .ifEmpty { null }
            status = parseStatus(this@toSManga.status)
        }
    }

    private fun parseStatus(status: List<Int>?): Int = when {
        status == null -> SManga.UNKNOWN
        status.contains(12) -> SManga.ONGOING
        status.contains(19) -> SManga.COMPLETED
        status.contains(174) -> SManga.ON_HIATUS
        status.contains(198) -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun ChapterItemDto.toSChapter(mangaSlug: String): SChapter = SChapter.create().apply {
        url = "$mangaSlug/$slug#$id"
        val cleanTitle = title.trim()
        name = "#$chapterNumber" + if (cleanTitle.isNotBlank()) " - $cleanTitle" else ""
        chapter_number = chapterNumber.toFloatOrNull() ?: -1f
        date_upload = dateFormat.tryParseDateTime(releaseDate)
    }

    private fun String?.toThumbnailUrl(): String? {
        val path = this?.trim().orEmpty()
        if (path.isEmpty()) return null
        if (path.startsWith("http", ignoreCase = true)) return path

        return uploadsUrl.newBuilder()
            .addEncodedPathSegments(path.removePrefix("/"))
            .build()
            .toString()
    }

    companion object {
        private const val CHAPTERS_PER_PAGE = 50
        private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
