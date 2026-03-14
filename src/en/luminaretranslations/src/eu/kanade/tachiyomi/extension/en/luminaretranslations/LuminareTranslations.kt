package eu.kanade.tachiyomi.extension.en.luminaretranslations

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class LuminareTranslations : HttpSource() {
    override val name = "Luminare Translations"
    override val baseUrl = "https://luminaretranslations.com"
    override val lang = "en"
    override val supportsLatest = true

    private val apiUrl = "$baseUrl/api"
    private val cdnUrl = "$baseUrl/storage"
    private val pageSize = 24

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override val client = network.cloudflareClient

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(SortFilter(listOf(Filters("popular", "")))))

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(SortFilter(listOf(Filters("latest", "")))))

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", pageSize.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("search", query)
        }

        filters.firstInstanceOrNull<SortFilter>()?.selected
            ?.let { url.addQueryParameter("sort", it) }

        filters.firstInstanceOrNull<GenreFilter>()?.state
            ?.filter { it.state }
            ?.joinToString(",") { it.slug }
            ?.takeIf { it.isNotEmpty() }
            ?.let { url.addQueryParameter("genres", it) }

        filters.firstInstanceOrNull<TagFilter>()?.state
            ?.filter { it.state }
            ?.joinToString(",") { it.slug }
            ?.takeIf { it.isNotEmpty() }
            ?.let { url.addQueryParameter("tags", it) }

        filters.firstInstanceOrNull<AuthorFilter>()?.selected
            ?.takeIf { it.isNotEmpty() }
            ?.let { url.addQueryParameter("author", it) }

        filters.firstInstanceOrNull<ArtistFilter>()?.selected
            ?.takeIf { it.isNotEmpty() }
            ?.let { url.addQueryParameter("artist", it) }

        filters.firstInstanceOrNull<TypeFilter>()?.selected
            ?.takeIf { it.isNotEmpty() }
            ?.let { url.addQueryParameter("type", it) }

        filters.firstInstanceOrNull<StatusFilter>()?.selected
            ?.takeIf { it.isNotEmpty() }
            ?.let { url.addQueryParameter("status", it) }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<EntryResponse>()
        val mangas = result.data.filter { it.type !in EXCLUDED_TYPES }.map { it.toSManga(cdnUrl) }
        val page = response.request.url.queryParameter("page")!!.toInt()
        val hasNextPage = (page * pageSize) < result.meta.total
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/series/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<DetailsResponse>().data.toSManga(cdnUrl)

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override fun chapterListRequest(manga: SManga): Request = GET("$apiUrl/series/${manga.url}/chapters?per_page=999", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val entrySlug = response.request.url.pathSegments[2]
        return response.parseAs<ChapterResponse>().data.map { it.toSChapter(entrySlug) }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/series/${chapter.url}"

    override fun pageListRequest(chapter: SChapter): Request = GET("$apiUrl/series/${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<ViewerResponse>().data
        return result.images.map {
            Page(it.order, imageUrl = "$cdnUrl/${it.imagePath}")
        }
    }

    private var filterData: FilterResponse? = null

    private fun fetchFilters() {
        if (filterData != null) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val request = GET("$apiUrl/explore/filters", headers)
                val response = client.newCall(request).execute()
                filterData = response.parseAs<FilterResponse>()
            }
        }
    }

    override fun getFilterList(): FilterList {
        fetchFilters()
        val data = filterData
        return if (data == null) {
            FilterList(Filter.Header("Press 'Reset' to load filters"))
        } else {
            FilterList(
                Filter.Header("Note: Search and active filters are applied together"),
                SortFilter(data.sorts),
                TypeFilter(data.types.filter { it.name !in EXCLUDED_TYPES }),
                StatusFilter(data.statuses),
                Filter.Separator(),
                GenreFilter(data.genres),
                TagFilter(data.tags),
                AuthorFilter(data.authors),
                ArtistFilter(data.artists),
            )
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val EXCLUDED_TYPES = setOf("novel", "light_novel", "web_novel")
    }
}
