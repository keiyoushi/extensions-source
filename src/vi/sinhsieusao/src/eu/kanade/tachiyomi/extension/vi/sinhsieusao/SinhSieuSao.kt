package eu.kanade.tachiyomi.extension.vi.sinhsieusao

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap

@Source
abstract class SinhSieuSao : HttpSource() {

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Accept", "application/json")

    private val workToMangaId = ConcurrentHashMap<Int, Int>()
    private val albumIds = ConcurrentHashMap<Int, Int>()

    // ============================== Popular =======================================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/api/v1/works/top".toHttpUrl().newBuilder()
            .addQueryParameter("period", "monthly")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<TopWorksResponse>()
        val mangas = result.items.map {
            it.toSManga().apply { thumbnail_url = "$baseUrl${it.coverUrl}" }
        }
        return MangasPage(mangas, false)
    }

    // ============================== Latest ========================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/api/v1/works".toHttpUrl().newBuilder()
            .addQueryParameter("items", "20")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<WorksResponse>()
        val mangas = result.items.map {
            it.toSManga().apply { thumbnail_url = "$baseUrl${it.coverUrl}" }
        }
        val hasNextPage = result.meta.pagy.page < result.meta.pagy.pages
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Search ========================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = filters.ifEmpty { getFilterList() }

        val kindFilter = filterList.firstInstanceOrNull<KindFilter>()
        val sortFilter = filterList.firstInstanceOrNull<SortFilter>()
        val tagsFilter = filterList.firstInstanceOrNull<TagsFilter>()

        val url = "$baseUrl/api/v1/works".toHttpUrl().newBuilder()
            .addQueryParameter("items", "20")
            .addQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("q", query)
        }

        kindFilter?.let {
            if (it.state > 0) {
                url.addQueryParameter("kind", it.toUriPart())
            }
        }

        sortFilter?.let {
            if (it.state > 0) {
                url.addQueryParameter("sort", it.toUriPart())
            }
        }

        tagsFilter?.let { filter ->
            val includedSlugs = filter.state
                .filterIsInstance<TagTriStateFilter>()
                .filter { it.state == Filter.TriState.STATE_INCLUDE }
                .map { it.slug }
            for (slug in includedSlugs) {
                url.addQueryParameter("tag", slug)
            }

            val excludedSlugs = filter.state
                .filterIsInstance<TagTriStateFilter>()
                .filter { it.state == Filter.TriState.STATE_EXCLUDE }
                .map { it.slug }
            for (slug in excludedSlugs) {
                url.addQueryParameter("exclude_tag", slug)
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<WorksResponse>()
        val mangas = result.items.map {
            it.toSManga().apply { thumbnail_url = "$baseUrl${it.coverUrl}" }
        }
        val hasNextPage = result.meta.pagy.page < result.meta.pagy.pages
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Details =======================================

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = "$baseUrl/api/v1/works/${manga.url}".toHttpUrl()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val work = response.parseAs<WorkDto>()
        when (work.kind) {
            "manga" -> work.workableId?.let { workToMangaId[work.id] = it }
            "album" -> work.workableId?.let { albumIds[work.id] = it }
        }
        return work.toSManga().apply { thumbnail_url = "$baseUrl${work.coverUrl}" }
    }

    // ============================== Chapters ======================================

    override fun chapterListRequest(manga: SManga): Request {
        val workId = manga.url
        val albumId = albumIds[workId.toIntOrNull()]
        if (albumId != null) {
            return GET("$baseUrl/api/v1/albums/$albumId?limit=200&offset=0&photos_sort=oldest".toHttpUrl(), headers)
        }
        val mangaId = workToMangaId[workId.toIntOrNull()]
            ?: fetchMangaId(workId)
        val url = "$baseUrl/api/v1/mangas/$mangaId".toHttpUrl()
        return GET(url, headers)
    }

    private fun fetchMangaId(workId: String): Int {
        val url = "$baseUrl/api/v1/works/$workId".toHttpUrl()
        val work = client.newCall(GET(url, headers)).execute().parseAs<WorkDto>()
        when (work.kind) {
            "manga" -> work.workableId?.let { workToMangaId[work.id] = it }
            "album" -> work.workableId?.let { albumIds[work.id] = it }
        }
        return work.workableId ?: work.id
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val url = response.request.url.toString()

        // Some works (album/oneshot) don't have chapters — they show images directly.
        // Create a placeholder "Oneshot".
        if (url.contains("/albums/")) {
            val album = response.parseAs<AlbumResponse>()
            return listOf(
                SChapter.create().apply {
                    this.url = "album:${album.id}"
                    name = "Oneshot"
                    chapter_number = 1f
                    date_upload = DATE_FORMAT.tryParse(album.createdAt)
                },
            )
        }

        val manga = response.parseAs<MangaDto>()
        return manga.chapters
            .filter { it.processingStatus == "processed" }
            .sortedByDescending { it.order }
            .map(ChapterDto::toSChapter)
    }

    // ============================== Pages =========================================

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrl = chapter.url

        if (chapterUrl.startsWith("album:")) {
            val albumId = chapterUrl.removePrefix("album:")
            val url = "$baseUrl/api/v1/albums/$albumId?limit=200&offset=0&photos_sort=oldest".toHttpUrl()
            return GET(url, headers)
        }

        val chapterId = chapterUrl
        val url = "$baseUrl/api/v1/chapters/$chapterId".toHttpUrl()
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val url = response.request.url.toString()

        if (url.contains("/albums/")) {
            val album = response.parseAs<AlbumResponse>()
            return album.photos
                .sortedBy { it.order }
                .mapIndexed { index, photo ->
                    val imageUrl = baseUrl + photo.imageUrl
                    Page(index, imageUrl, imageUrl)
                }
        }

        val chapter = response.parseAs<ChapterDetailDto>()
        return chapter.pages
            .sortedBy { it.order }
            .mapIndexed { index, page ->
                val imageUrl = baseUrl + page.imageUrl
                Page(index, imageUrl, imageUrl)
            }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters =======================================

    private var genreList: List<GenreItem> = emptyList()

    override fun getFilterList(): FilterList {
        fetchGenreListIfNeeded()
        return if (genreList.isEmpty()) {
            FilterList(KindFilter(), SortFilter())
        } else {
            FilterList(
                KindFilter(),
                TagsFilter(genreList),
                SortFilter(),
            )
        }
    }

    private fun fetchGenreListIfNeeded() {
        if (genreList.isNotEmpty()) return
        genreList = try {
            val tags = mutableListOf<GenreItem>()
            var page = 1
            var totalPages = 1
            do {
                val url = "$baseUrl/api/v1/tags".toHttpUrl().newBuilder()
                    .addQueryParameter("page", page.toString())
                    .build()
                val result = client.newCall(GET(url, headers)).execute().use { response ->
                    response.parseAs<TagsResponse>()
                }
                tags.addAll(result.items.map { GenreItem(it.id, it.name, it.slug) })
                totalPages = result.meta.pagy.pages
                page++
            } while (page <= totalPages)
            tags
        } catch (_: Exception) {
            emptyList()
        }
    }
}
