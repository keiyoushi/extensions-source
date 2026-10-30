package eu.kanade.tachiyomi.extension.es.mangashiina

import eu.kanade.tachiyomi.network.HttpException
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
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class MangaMukai : KeiSource() {

    private val apiUrl get() = "$baseUrl/wp-json/mangamukai/v1"

    private val wpUrl get() = "$baseUrl/wp-json/wp/v2"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2) { it.encodedPath.startsWith("/wp-json/") }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter(SortFilter.VIEWS)))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter(SortFilter.UPDATED)))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val words = query.normalized().split(WHITESPACE).filter(String::isNotEmpty)
        val type = filters.firstInstanceOrNull<TypeFilter>()?.selected
        val genres = filters.firstInstanceOrNull<GenreFilter>()
        val sort = filters.firstInstanceOrNull<SortFilter>() ?: SortFilter()

        val mangas = fetchCatalog().filter { manga ->
            val title = manga.titulo.normalized()
            words.all { it in title } &&
                (type == null || manga.tipo == type) &&
                genres?.matches(manga.genres) != false
        }

        return MangasPage(sort.sort(mangas).map { it.toSManga() }, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments[0] != "manga") return null
        val segment = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null

        return getMangaUpdate(
            manga = SManga.create().apply { this.url = resolveMangaId(segment) },
            chapters = emptyList(),
            fetchDetails = true,
            fetchChapters = false,
        ).manga
    }

    override fun getMangaUrl(manga: SManga): String {
        val id = manga.memo[MANGA_ID]?.stringOrNull ?: manga.url.takeUnless { it.startsWith("/") } ?: return baseUrl + manga.url
        return "$baseUrl/manga/$id"
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val id = mangaId(manga)
        val details = async {
            if (fetchDetails) client.get("$apiUrl/manga/$id").parseAs<DetailsDto>().toSManga(manga.url) else manga
        }
        val chapterList = async {
            if (fetchChapters) client.get("$apiUrl/series/$id/chapters").parseAs<ChaptersDto>().chapters.asReversed().map { it.toSChapter() } else chapters
        }

        SMangaUpdate(details.await().apply { memo = buildJsonObject { put(MANGA_ID, id) } }, chapterList.await())
    }

    override fun getChapterUrl(chapter: SChapter): String = if (chapter.url.startsWith("/")) baseUrl + chapter.url else "$baseUrl/read/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        // Chapters saved by the MangaThemesia version store the post slug
        if (chapter.url.startsWith("/")) throw Exception("Refresh chapter list")
        return client.get("$apiUrl/chapters/content?id=${chapter.url}", ensureSuccess = false).use { response ->
            // Paid chapters answer 401 until they're bought with the site's coins
            if (response.code == 401) return emptyList()
            if (!response.isSuccessful) throw HttpException(response.code)
            response.parseAs<PagesDto>().images.mapIndexed { index, image -> Page(index, imageUrl = image) }
        }
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get("$apiUrl/related/${mangaId(manga)}?limit=10")
        .parseAs<CatalogDto>().mangas.map { it.toSManga() }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val mangas = fetchCatalog()
        return FilterData(
            types = mangas.mapNotNull { it.tipo }.distinct().sortedBy { it.normalized() },
            genres = mangas.flatMap { it.genres }.distinct().sortedBy { it.normalized() },
        ).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>()

        return FilterList(
            buildList {
                add(SortFilter())
                if (filterData != null) {
                    add(TypeFilter(filterData.types))
                    add(GenreFilter(filterData.genres))
                }
            },
        )
    }

    private suspend fun fetchCatalog(): List<MangaDto> = client.get("$apiUrl/catalog").parseAs<CatalogDto>().mangas

    private suspend fun mangaId(manga: SManga): String = manga.memo[MANGA_ID]?.stringOrNull ?: resolveMangaId(manga.url)

    // Entries saved by the MangaThemesia version store "/manga/<slug>/", and the slug matches the series' chapter category
    private suspend fun resolveMangaId(url: String): String {
        if (url.all(Char::isDigit)) return url
        val slug = url.trimEnd('/').substringAfterLast('/')
        val category = client.get("$wpUrl/categories?slug=$slug&_fields=id").parseAs<List<IdDto>>().first()
        return client.get("$wpUrl/posts?categories=${category.id}&per_page=1&_fields=ero_seri").parseAs<List<SeriesRefDto>>().first().eroSeri
    }

    companion object {
        private const val MANGA_ID = "id"
        private val WHITESPACE = Regex("""\s+""")
    }
}
