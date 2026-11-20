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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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
            "chapter" -> fetchReader(segments[1]).first.manga.slug
            else -> return null
        }
        return fetchMangaPage(slug).manga.toSMangaDetails().apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/chapter/${chapter.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val page = fetchMangaPage(manga.url)
        return SMangaUpdate(
            if (fetchDetails) page.manga.toSMangaDetails() else manga,
            if (fetchChapters) fetchChapters(page.initialChapters) else chapters,
        )
    }

    private suspend fun fetchMangaPage(slug: String) = client.get("$baseUrl/manga/$slug", Fetch.DOCUMENT)
        .extractNextJs<MangaPageDto> { it is JsonObject && "manga" in it && "initialChapters" in it }!!

    // The series page lists only the newest chapters with dates; the reader page lists every chapter, without dates
    private suspend fun fetchChapters(initial: ChapterListDto): List<SChapter> {
        val newest = initial.chapters.firstOrNull() ?: return emptyList()
        if (!initial.hasMore) return initial.chapters.map { it.toSChapter() }
        val dated = initial.chapters.associateBy { it.id }
        return client.get("$baseUrl/chapter/${newest.id}", Fetch.DOCUMENT)
            .extractNextJs<ReaderChaptersDto> { it is JsonObject && "allChapters" in it }!!
            .allChapters
            .map { (dated[it.id] ?: it).toSChapter() }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (reader, rk) = fetchReader(chapter.url)
        val (pages, signatures) = if (!reader.encryptedPages.isNullOrEmpty() && !rk.isNullOrEmpty()) {
            decryptReader(reader.encryptedPages, rk)
        } else {
            reader.pages.filterNot { it.contains("/_mihon/") } to reader.imageSigs
        }
        return pages.mapIndexed { index, url -> Page(index, imageUrl = proxyUrl(url, signatures[url])) }
    }

    // The chapter API serves placeholder pages; the reader page carries the real ones
    private suspend fun fetchReader(chapterId: String): Pair<ReaderDto, String?> {
        val document = client.get("$baseUrl/chapter/$chapterId", Fetch.DOCUMENT).asJsoup()
        val reader = document.extractNextJs<ReaderDto>()!!
        val rk = RK_REGEX.find(document.html())?.groupValues?.get(1)?.trim('"', '\'', '\\', ' ')
        return reader to rk
    }

    private fun decryptReader(encrypted: String, rk: String): Pair<List<String>, Map<String, String>> {
        val rBytes = Base64.decode(encrypted, Base64.URL_SAFE)
        val keyBytes = rk.toByteArray()
        val decrypted = ByteArray(rBytes.size) { i ->
            (rBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt() xor (i and 0xFF)).toByte()
        }
        val dto = decrypted.decodeToString().parseAs<ReaderDecryptedDto>()
        return dto.pages to dto.signatures
    }

    // The site's reader loads every page through its image proxy, signed, on the server its script picks for the URL
    private fun proxyUrl(url: String, signature: String?): String {
        val encoded = Base64.encodeToString(url.toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val server = IMAGE_SERVERS[(url.hashCode().toLong().absoluteValue % IMAGE_SERVERS.size).toInt()]
        return "$server/v/$encoded" + signature?.let { "?s=$it" }.orEmpty()
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = fetchFilters().toJsonElement()

    private suspend fun fetchFilters() = client.get("$baseUrl/catalog", Fetch.DOCUMENT)
        .extractNextJs<FiltersDto>()!!

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
        private val RK_REGEX = Regex("""--rk:\s*\\?"?([^;\s"\\{}]+)""")
        private const val API_URL = "https://b78sk.komiwa.net"
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
