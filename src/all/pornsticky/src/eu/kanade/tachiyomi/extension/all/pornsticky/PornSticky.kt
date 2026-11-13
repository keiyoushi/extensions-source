package eu.kanade.tachiyomi.extension.all.pornsticky

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class PornSticky : KeiSource() {

    // The home page lists the site's categories: there is no list of all its galleries, and no latest one
    override val supportsLatest = false

    // The widest list the site has: its search for "porn"
    override suspend fun getPopularManga(page: Int) = getGalleryList(listOf("search", "porn"), page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // Like the site's search form: letters, digits and spaces, the words joined with "-"
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val slug = query.lowercase().replace(UNSEARCHABLE, "").trim().replace(SPACES, "-")
        val name = slug.ifEmpty { filters.firstInstanceOrNull<CategoryFilter>()?.selected } ?: return getPopularManga(page)
        return getGalleryList(listOf("search", name), page)
    }

    // A search for a category, model or paysite redirects to its list, and its next pages stay under /search/
    private suspend fun getGalleryList(segments: List<String>, page: Int): MangasPage {
        val listUrl = baseUrl.toHttpUrl().newBuilder().apply {
            segments.forEach(::addPathSegment)
            if (page > 1) addPathSegment(page.toString())
            addPathSegment("")
        }.build()
        val document = try {
            client.get(listUrl, Fetch.DOCUMENT).asJsoup()
        } catch (e: HttpException) {
            // Searches without results and pages past the last one answer 410
            if (e.code != 410) throw e
            return MangasPage(emptyList(), false)
        }
        val mangas = document.select("#container > li > a[href*=/pics/]").map { link ->
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                // Search results end the title with the gallery's id
                title = link.attrOrNull("title")!!.removeSuffix(" " + url.trimEnd('/').substringAfterLast('-'))
                thumbnail_url = link.selectFirst("img")?.absUrl("data-src")
            }
        }.distinctBy { it.url }
        // Model lists have no pager, yet go on past their first page
        val hasNextPage = document.selectFirst("a.next") != null || (document.selectFirst(".pagination") == null && mangas.size >= PAGE_SIZE)
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val listPage = segments.lastOrNull()?.toIntOrNull()
        val list = if (listPage != null) segments.dropLast(1) else segments
        val listSegments = when {
            url.host.removePrefix("www.") != baseUrl.toHttpUrl().host -> null
            list.size == 2 && list[0] in LIST_TYPES -> list
            // A category's first page is /<category>/, and the site's pager goes on with /search/<category>/<page>/
            list.size == 1 && list[0] !in LIST_TYPES && list[0] !in OTHER_PAGES -> listOf("search", list[0])
            else -> null
        } ?: return super.getMangasByUrl(url, page)
        return getGalleryList(listSegments, (listPage ?: 1) + page - 1)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host || segments.size != 2 || segments[0] != "pics") return null
        val manga = SManga.create().apply { this.url = "/pics/${segments[1]}/" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val photos = document.select(PHOTO_SELECTOR)
        val details = SManga.create().apply {
            url = manga.url
            title = document.selectFirst(".breadcrumbs h1")!!.text()
            author = document.selectFirst("[data-models-related]")?.attr("data-models-related")
                ?.split(',')?.mapNotNull { it.trim().ifEmpty { null } }?.joinToString()?.ifEmpty { null }
            artist = document.selectFirst(".breadcrumbs a")?.text()
            genre = document.select(".content-categories a:not(.person)").joinToString { it.text() }.ifEmpty { null }
            description = MarkdownDescription.Builder().info("${photos.size} photos").build()
            thumbnail_url = manga.thumbnail_url ?: photos.firstOrNull()?.selectFirst("img")?.absUrl("data-src")
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Gallery"
        }
        return SMangaUpdate(details, listOf(chapter))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select(PHOTO_SELECTOR).mapIndexed { index, link -> Page(index, imageUrl = link.absUrl("href")) }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        .select("#container > li > a").map { link -> CategoryDto(link.attrOrNull("title")!!, link.absUrl("href").toHttpUrl().pathSegments[0]) }
        .sortedBy { it.name.lowercase() }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        listOfNotNull(
            Filter.Header("A text search leaves the category out"),
            data?.parseAs<List<CategoryDto>>()?.let(::CategoryFilter),
        ),
    )

    companion object {
        private const val PAGE_SIZE = 160
        private const val PHOTO_SELECTOR = "#container > li > a.ss-image"
        private val LIST_TYPES = setOf("search", "pornstars", "porn-sites")
        private val OTHER_PAGES = setOf("pics", "sex-chat", "to", "contact")
        private val UNSEARCHABLE = Regex("[^a-z0-9 ]")
        private val SPACES = Regex(" +")
    }
}
