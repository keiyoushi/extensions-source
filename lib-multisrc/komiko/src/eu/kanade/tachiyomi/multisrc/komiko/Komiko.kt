package eu.kanade.tachiyomi.multisrc.komiko

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getArray
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

/**
 * Shops of French manga publishers: an Angular app in front of a JSON API. Only the free previews
 * of the volumes can be read, so every volume is an entry with a single chapter.
 */
abstract class Komiko : KeiSource() {

    /**
     * Base of the shop's API.
     */
    protected open val apiUrl: String get() = "https://api.${baseUrl.toHttpUrl().host.removePrefix("www.")}/v2"

    /**
     * Path of a volume's page on the site, before `<slug>/<ref>`.
     */
    protected open val productPath = "catalogue/produit"

    /**
     * Whether lists only keep the volumes sold as ebooks, the only ones with a preview on some shops.
     */
    protected open val ebookOnly = true

    override val supportsFilterFetching get() = true

    override suspend fun getPopularManga(page: Int) = catalogue(page, SORT_POPULARITY)

    override suspend fun getLatestUpdates(page: Int) = catalogue(page, SORT_RELEASE)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.state ?: SORT_POPULARITY
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty().filter { it.state }.map { it.id }
        val allGenres = filters.firstInstanceOrNull<GenreModeFilter>()?.state == 1
        return catalogue(page, sort, query, genres, allGenres)
    }

    private suspend fun catalogue(page: Int, sort: Int, query: String = "", genres: List<Int> = emptyList(), allGenres: Boolean = false): MangasPage {
        val data = client.get(catalogueUrl(page, PAGE_SIZE, sort, query, genres, allGenres), Fetch.XHR).parseApi<CatalogueDto>()
        return MangasPage(data.produits.map { it.toSManga() }, page * PAGE_SIZE < data.nb)
    }

    private fun catalogueUrl(page: Int, pageSize: Int, sort: Int, query: String, genres: List<Int>, allGenres: Boolean, init: Boolean = false): HttpUrl = "$apiUrl/catalogue/".toHttpUrl().newBuilder()
        .addQueryParameter("type", "manga")
        .apply {
            if (genres.isNotEmpty()) {
                addQueryParameter("genres", genres.joinToString())
                if (allGenres) addQueryParameter("genres_mode", "et")
            }
        }
        .addQueryParameter("q", query)
        .addQueryParameter("init", init.toString())
        .addQueryParameter("tri", sort.toString())
        .addQueryParameter("pageSize", pageSize.toString())
        .addQueryParameter("pageIndex", (page - 1).toString())
        .apply { if (ebookOnly) addQueryParameter("ebook", "1") }
        .build()

    override fun getMangaUrl(manga: SManga) = "$baseUrl/$productPath/${manga.url}"

    // Details and chapter come from the same answer
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val answer = product(manga.url.substringAfterLast('/'))
        val product = answer.produit ?: throw Exception(answer.message)
        return SMangaUpdate(product.toSManga(), product.toChapters())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return null
        val path = url.encodedPath.trim('/')
        val ref = when {
            path.startsWith("$productPath/") -> path.substringAfterLast('/')
            path.startsWith("lecteur-ebook/") -> path.substringAfter('/').substringBefore('/')
            else -> return null
        }
        if (ref.isEmpty() || !ref.all(Char::isDigit)) return null
        return product(ref).produit?.toSManga()?.apply { initialized = true }
    }

    private suspend fun product(ref: String): ProductDto = client.get("$apiUrl/produit/?id=$ref", Fetch.XHR).parseApi()

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/lecteur-ebook/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> = chapter.memo.getArray("pages").mapIndexed { i, url -> Page(i, imageUrl = url.string) }

    override suspend fun fetchFilterData(): JsonElement = client.get(catalogueUrl(1, 1, SORT_POPULARITY, "", emptyList(), false, init = true), Fetch.XHR)
        .parseApi<FiltersDto>().genres.toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreDto>>().orEmpty()
        return FilterList(
            buildList {
                add(SortFilter())
                if (genres.isNotEmpty()) {
                    add(GenreModeFilter())
                    add(GenreFilter(genres))
                }
            },
        )
    }

    // Every answer starts with a line against JSON hijacking: )]}',
    private inline fun <reified T> Response.parseApi(): T = parseAs<T> { it.substringAfter('\n') }

    companion object {
        private const val PAGE_SIZE = 48
        private const val SORT_POPULARITY = 0
        private const val SORT_RELEASE = 2
    }
}
