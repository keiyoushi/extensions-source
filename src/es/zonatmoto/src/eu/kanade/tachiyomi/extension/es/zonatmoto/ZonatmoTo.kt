package eu.kanade.tachiyomi.extension.es.zonatmoto

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class ZonatmoTo : HttpSource() {

    override val name = "Zonatmo.to (unoriginal)"

    override val baseUrl = "https://zonatmo.to"

    override val lang = "es"

    override val supportsLatest = false

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()

    private val apiHeaders: Headers by lazy {
        headersBuilder()
            .add("Referer", "$baseUrl/")
            .build()
    }

    // ========================= Popular =========================

    override fun popularMangaRequest(page: Int): Request {
        val url = apiUrl.newBuilder()
            .addPathSegments("tops/views/month")
            .addQueryParameter("postType", "any")
            .addQueryParameter("postsPerPage", "50")
            .build()

        return GET(url, apiHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<TopViewsResponseDto>()
        val mangas = dto.data
            ?.items
            .orEmpty()
            .mapNotNull { it.toSManga() }

        return MangasPage(mangas, false)
    }

    // ========================= Latest =========================

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ========================= Search =========================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val mangaSlug = query.toHttpUrlOrNull()
            ?.takeIf(::isSupportedDeeplink)
            ?.pathSegments
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }

        val genreFilter = filters.filterIsInstance<GenreFilter>().firstOrNull()
        val selectedGenres = genreFilter?.state?.filter { it.state }?.map { it.value }.orEmpty()

        val typeFilter = filters.filterIsInstance<TypeFilter>().firstOrNull()
        val selectedTypes = typeFilter?.state?.filter { it.state }?.map { it.value }.orEmpty()

        val statusFilter = filters.filterIsInstance<StatusFilter>().firstOrNull()
        val selectedStatuses = statusFilter?.state?.filter { it.state }?.map { it.value }.orEmpty()

        val url = if (mangaSlug != null) {
            singleMangaApiUrl(mangaSlug)
        } else {
            listingMangaApiUrl(
                page = page,
                searchQuery = query.trim().takeIf { it.isNotEmpty() },
                genres = selectedGenres,
                types = selectedTypes,
                statuses = selectedStatuses,
            )
        }

        return GET(url, apiHeaders)
    }

    // ========================= Filters =========================

    override fun getFilterList() = FilterList(
        GenreFilter(),
        TypeFilter(),
        StatusFilter(),
    )

    override fun searchMangaParse(response: Response): MangasPage {
        val requestPath = response.request.url.encodedPath

        return if (requestPath.contains("/single/manga/")) {
            val manga = response.parseAs<SingleMangaResponseDto>()
                .data
                ?.toSManga()
                ?.let(::listOf)
                .orEmpty()

            MangasPage(manga, false)
        } else {
            listingMangaParse(response)
        }
    }

    private fun listingMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<ListingResponseDto>()
        val mangas = dto.data
            ?.items
            .orEmpty()
            .mapNotNull { it.toSManga() }

        val hasNextPage = dto.data?.pagination?.hasNext ?: false

        return MangasPage(mangas, hasNextPage)
    }

    // ========================= Details =========================

    override fun getMangaUrl(manga: SManga): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("manga")
        .addPathSegment(manga.url)
        .build()
        .toString()

    override fun mangaDetailsRequest(manga: SManga): Request = GET(singleMangaApiUrl(manga.url), apiHeaders)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<SingleMangaResponseDto>()
        .data
        ?.toSManga()
        ?: throw Exception("Unable to parse manga details")

    // ========================= Chapters =========================

    override fun getChapterUrl(chapter: SChapter): String {
        val slugs = chapter.url.split("/", limit = 2)
            .takeIf { it.size == 2 }
            ?: return baseUrl

        return baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("manga")
            .addPathSegment(slugs[0])
            .addPathSegment(slugs[1])
            .build()
            .toString()
    }

    override fun chapterListRequest(manga: SManga): Request = GET(
        chapterListApiUrl(mangaSlug = manga.url, page = 1),
        apiHeaders,
    )

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaSlug = response.request.url.pathSegments
            .windowed(size = 2)
            .firstOrNull { it.first() == "manga" }
            ?.last()
            ?.takeIf { it.isNotBlank() }
            ?: return emptyList()

        val firstPageDto = response.parseAs<ChapterListResponseDto>()
        val chapters = firstPageDto.data?.items.orEmpty().toMutableList()

        val totalPages = firstPageDto.data?.pagination?.totalPages ?: 1
        for (page in 2..totalPages) {
            val nextPageResponse = client.newCall(
                GET(chapterListApiUrl(mangaSlug = mangaSlug, page = page), apiHeaders),
            ).execute()

            nextPageResponse.use {
                chapters += it.parseAs<ChapterListResponseDto>().data?.items.orEmpty()
            }
        }

        return chapters
            .distinctBy { item -> item.id }
            .sortedByDescending { item -> item.chapterNumber.toFloatOrNull() ?: -1f }
            .map { item -> item.toSChapter(mangaSlug) }
    }

    // ========================= Pages =========================

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrl = baseUrl.toHttpUrl().resolve(chapter.url)!!

        val pathSegments = chapterUrl.pathSegments
        val mangaSlug = pathSegments[pathSegments.size - 2]
        val chapterSlug = pathSegments[pathSegments.size - 1]

        return GET(singleChapterApiUrl(mangaSlug, chapterSlug), apiHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapter = response.parseAs<SingleChapterResponseDto>()
            .data
            ?.chapter
            ?: throw Exception("Unable to parse chapter pages")

        return chapter.images
            .sortedBy(ChapterImageDto::pageNumber)
            .mapIndexed { index, image ->
                Page(
                    index = index,
                    imageUrl = cdnImageUrl(chapter.jit, image.imageUrl),
                )
            }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ========================= Utilities =========================

    private fun isSupportedDeeplink(url: HttpUrl): Boolean {
        if (url.host != SOURCE_HOST && url.host != "www.$SOURCE_HOST") return false

        val pathSegments = url.pathSegments
        return pathSegments.getOrNull(0) == "manga" && !pathSegments.getOrNull(1).isNullOrBlank()
    }

    private fun listingMangaApiUrl(
        page: Int,
        searchQuery: String? = null,
        genres: List<String> = emptyList(),
        types: List<String> = emptyList(),
        statuses: List<String> = emptyList(),
    ): String {
        val url = apiUrl.newBuilder()
            .addPathSegments("listing/manga")
            .addQueryParameter("page", page.toString())

        searchQuery?.let { url.addQueryParameter("search", it) }
        for (genreId in genres) {
            url.addQueryParameter("genres[]", genreId)
        }
        for (typeId in types) {
            url.addQueryParameter("type[]", typeId)
        }
        for (statusId in statuses) {
            url.addQueryParameter("status[]", statusId)
        }

        return url.build().toString()
    }

    private fun singleMangaApiUrl(mangaSlug: String): String = apiUrl.newBuilder()
        .addPathSegment("single")
        .addPathSegment("manga")
        .addPathSegment(mangaSlug)
        .build()
        .toString()

    private fun chapterListApiUrl(mangaSlug: String, page: Int): String = apiUrl.newBuilder()
        .addPathSegment("single")
        .addPathSegment("manga")
        .addPathSegment(mangaSlug)
        .addPathSegment("chapters")
        .addQueryParameter("page", page.toString())
        .addQueryParameter("postsPerPage", CHAPTERS_PER_PAGE.toString())
        .addQueryParameter("order", "asc")
        .build()
        .toString()

    private fun singleChapterApiUrl(mangaSlug: String, chapterSlug: String): String {
        val url = apiUrl.newBuilder()
            .addPathSegment("single")
            .addPathSegment("manga")
            .addPathSegment(mangaSlug)
            .addPathSegment(chapterSlug)

        return url.build().toString()
    }

    private fun cdnImageUrl(jit: String, imageName: String): String = CDN_URL.toHttpUrl().newBuilder()
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
                .map { it.name }
                .map(String::trim)
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
        date_upload = dateFormat.tryParse(releaseDate)
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
        private const val SOURCE_HOST = "zonatmo.to"
        private const val CDN_URL = "https://cdn.$SOURCE_HOST"
        private const val CHAPTERS_PER_PAGE = 50

        private val apiUrl = "https://$SOURCE_HOST/wp-api/api".toHttpUrl()
        private val uploadsUrl = "https://$SOURCE_HOST/wp-content/uploads".toHttpUrl()

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
    }
}
