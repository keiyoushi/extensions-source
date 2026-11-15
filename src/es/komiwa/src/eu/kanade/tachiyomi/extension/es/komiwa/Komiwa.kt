package eu.kanade.tachiyomi.extension.es.komiwa

import android.util.Base64
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.math.absoluteValue

@Source
abstract class Komiwa : KeiSource() {

    private val host get() = baseUrl.toHttpUrl().host.removePrefix("www.")

    override suspend fun getPopularManga(page: Int) = fetchCatalog(CatalogBodyDto(page, PAGE_SIZE, "views", "desc"))

    override suspend fun getLatestUpdates(page: Int) = fetchCatalog(CatalogBodyDto(page, PAGE_SIZE, "updatedAt", "desc"))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()
        return fetchCatalog(
            CatalogBodyDto(
                page = page,
                limit = PAGE_SIZE,
                sortBy = sort?.sortBy ?: "views",
                sortOrder = sort?.sortOrder ?: "desc",
                q = query.trim().ifEmpty { null },
                genres = filters.firstInstanceOrNull<GenreFilter>()?.checked,
                types = filters.firstInstanceOrNull<TypeFilter>()?.checked,
                demographics = filters.firstInstanceOrNull<DemographyFilter>()?.checked,
                status = filters.firstInstanceOrNull<StatusFilter>()?.selected,
                ageRating = filters.firstInstanceOrNull<AgeRatingFilter>()?.selected,
            ),
        )
    }

    private suspend fun fetchCatalog(body: CatalogBodyDto): MangasPage {
        val data = client.post("$API_URL/manga/catalog", Fetch.XHR.from("/catalog"), body.toJsonRequestBody())
            .parseAs<ResponseDto<CatalogDto>>()
            .data
        return MangasPage(data.items.map { it.toSManga() }, data.hasMore)
    }

    // Genre, type and demography pages are the catalog filtered by that value
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (url.host.removePrefix("www.") != host || segments.size != 2 || segments[0] !in LIST_PATHS) {
            return super.getMangasByUrl(url, page)
        }
        val filters = fetchFilters()
        val slug = segments[1]
        val body = when (segments[0]) {
            "genero" -> CatalogBodyDto(page, PAGE_SIZE, "views", "desc", genres = filters.genres.idOf(slug) ?: return MangasPage(emptyList(), false))
            "tipo" -> CatalogBodyDto(page, PAGE_SIZE, "views", "desc", types = filters.types.idOf(slug) ?: return MangasPage(emptyList(), false))
            else -> CatalogBodyDto(page, PAGE_SIZE, "views", "desc", demographics = filters.demographics.idOf(slug) ?: return MangasPage(emptyList(), false))
        }
        return fetchCatalog(body)
    }

    private fun List<TaxonomyDto>.idOf(slug: String) = firstOrNull { it.slug == slug }?.id?.let(::listOf)

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (url.host.removePrefix("www.") != host || segments.size != 2) return null
        val slug = when (segments[0]) {
            "manga" -> segments[1]
            "chapter" -> fetchReader(segments[1]).manga.slug
            else -> return null
        }
        return fetchManga(slug).toSMangaDetails().apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/chapter/${chapter.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val id = manga.memo["id"]?.stringOrNull
            ?: return fetchManga(manga.url).let { SMangaUpdate(it.toSMangaDetails(), if (fetchChapters) fetchChapters(it.id) else chapters) }
        return coroutineScope {
            val details = async { if (fetchDetails) fetchManga(manga.url).toSMangaDetails() else manga }
            val chapterList = async { if (fetchChapters) fetchChapters(id) else chapters }
            SMangaUpdate(details.await(), chapterList.await())
        }
    }

    private suspend fun fetchManga(slug: String) = client.get("$API_URL/manga/$slug", Fetch.XHR.from("/manga/$slug"))
        .parseAs<ResponseDto<MangaDto>>()
        .data

    // The chapter list takes the series id, not its slug
    private suspend fun fetchChapters(id: String): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var page = 1
        do {
            val data = client.get("$API_URL/manga/$id/chapters?page=$page&limit=500&sort=desc", Fetch.XHR)
                .parseAs<ResponseDto<ChapterListDto>>()
                .data
            chapters += data.chapters.map { it.toSChapter() }
            page++
        } while (data.hasMore)
        return chapters
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val reader = fetchReader(chapter.url)
        return reader.pages.mapIndexed { index, url -> Page(index, imageUrl = proxyUrl(url, reader.imageSigs[url])) }
    }

    private suspend fun fetchReader(chapterId: String) = client.get("$API_URL/manga/chapter/$chapterId", Fetch.XHR.from("/chapter/$chapterId"))
        .parseAs<ResponseDto<ReaderDto>>()
        .data

    // The site's reader loads every page through its image proxy, signed, on the server its script picks for the URL
    private fun proxyUrl(url: String, signature: String?): String {
        val encoded = Base64.encodeToString(url.toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val server = IMAGE_SERVERS[(url.hashCode().toLong().absoluteValue % IMAGE_SERVERS.size).toInt()]
        return "$server/v/$encoded" + signature?.let { "?s=$it" }.orEmpty()
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = fetchFilters().toJsonElement()

    private suspend fun fetchFilters() = client.get("$API_URL/manga/filters", Fetch.XHR.from("/catalog"))
        .parseAs<ResponseDto<FiltersDto>>()
        .data

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = data?.parseAs<FiltersDto>()
        return FilterList(
            listOfNotNull(
                SortFilter(),
                AgeRatingFilter(),
                filters?.let { StatusFilter(it.statuses) },
                filters?.let { TypeFilter(it.types) },
                filters?.let { DemographyFilter(it.demographics) },
                filters?.let { GenreFilter(it.genres) },
            ),
        )
    }

    companion object {
        private const val API_URL = "https://api.komiwaapp.lat"
        private const val PAGE_SIZE = 24
        private val LIST_PATHS = setOf("genero", "tipo", "demografia")
        private val IMAGE_SERVERS = listOf(
            "https://e5d7.komiwa.net",
            "https://h9s2.komiwa.net",
            "https://n7t9.komiwa.net",
            "https://x4v1.komiwa.net",
        )
    }
}
