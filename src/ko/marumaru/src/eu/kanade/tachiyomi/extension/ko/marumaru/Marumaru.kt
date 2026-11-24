package eu.kanade.tachiyomi.extension.ko.marumaru

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class Marumaru : KeiSource() {

    // Old domains redirect to the current one
    override suspend fun fetchBaseUrls(client: OkHttpClient): List<String> = listOf(client.get(baseUrl, Fetch.DOCUMENT).use { it.request.url.toString() })

    override suspend fun getPopularManga(page: Int): MangasPage = getWorks(page, WEBTOON, listOf("sort" to "VIEW"))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getWorks(page, WEBTOON, listOf("sort" to "LATEST"))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val type = filters.firstInstanceOrNull<TypeFilter>()?.value ?: WEBTOON
        val completed = filters.firstInstanceOrNull<StatusFilter>()?.completed == true
        val sort = filters.firstInstanceOrNull<SortFilter>()?.value ?: "LATEST"

        if (query.isNotBlank()) {
            val url = apiUrl("search")
                .addQueryParameter("q", query.trim())
                .addQueryParameter("page", (page - 1).toString())
                .addQueryParameter("media", type)
                .apply {
                    if (completed) addQueryParameter("status", "COMPLETED")
                    filters.firstInstanceOrNull<FieldFilter>()?.value?.let { addQueryParameter("field", it) }
                }
                .addQueryParameter("sort", sort.takeIf { it in SEARCH_SORTS } ?: "LATEST")
                .build()
            return client.get(url, Fetch.XHR).parseAs<WorksPageDto>().toMangasPage()
        }

        val params = buildList {
            add("sort" to sort)
            if (completed) add("completed" to "true")
            filters.firstInstanceOrNull<CategoryFilter>()?.param?.let(::add)
            filters.firstInstanceOrNull<WeekdayFilter>()?.value?.let { add("weekday" to it) }
            filters.firstInstanceOrNull<GenreFilter>()?.value?.let { add("genre" to it) }
            filters.firstInstanceOrNull<PlatformFilter>()?.value?.let { add("platform" to it) }
        }
        return getWorks(page, type, params)
    }

    private suspend fun getWorks(page: Int, type: String, params: List<Pair<String, String>>): MangasPage {
        val url = apiUrl("works").apply {
            addQueryParameter("mediaType", type)
            params.forEach { (name, value) -> addQueryParameter(name, value) }
            addQueryParameter("page", (page - 1).toString())
            addQueryParameter("size", PAGE_SIZE.toString())
        }.build()
        return client.get(url, Fetch.XHR).parseAs<WorksPageDto>().toMangasPage()
    }

    private fun apiUrl(path: String) = baseUrl.toHttpUrl().newBuilder().addPathSegment("api").addPathSegments(path)

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!FRONT_HOST.matches(url.host) || url.pathSegments.first() !in LINK_PATHS) return null
        val id = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: return null
        return getWork(id).takeIf { it.isComic }?.toSManga()?.apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val id = manga.url.substringAfterLast('/')
        return SMangaUpdate(
            manga = { if (fetchDetails) getWork(id).toSManga() else manga },
            chapters = { if (fetchChapters) getChapters(manga.url, id) else chapters },
        )
    }

    private suspend fun getWork(id: String): WorkDetailsDto = client.get(apiUrl("works").addPathSegment(id).build(), Fetch.XHR).parseAs()

    // Episodes that aren't ready yet have no images
    private suspend fun getChapters(mangaUrl: String, id: String): List<SChapter> {
        val url = READER_URL.toHttpUrl().newBuilder().addPathSegments("works/$id").build()
        return client.get(url, Fetch.XHR).parseAs<ReaderWorkDto>().episodes
            .filter { it.ready }
            .map { it.toSChapter(mangaUrl) }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = READER_URL.toHttpUrl().newBuilder().addPathSegments("episodes/${chapter.url.substringAfterLast('/')}").build()
        return client.get(url, Fetch.XHR).parseAs<ReaderEpisodeDto>().images
            .mapIndexed { i, imageUrl -> Page(i, imageUrl = imageUrl) }
    }

    override fun getFilterList(data: JsonElement?) = getFilters()

    companion object {
        private const val PAGE_SIZE = 30

        // The reader API the site itself calls
        private const val READER_URL = "https://api.storagewebtoon.store/api/v1/reader/A2"

        // The search only sorts by these
        private val SEARCH_SORTS = setOf("LATEST", "BOOKMARK", "VIEW", "EPISODE")

        private val FRONT_HOST = Regex("""(?:www\.)?marumaru\d+\.com""")
        private val LINK_PATHS = setOf("webtoon", "manhwa", "work")
    }
}

internal const val WEBTOON = "WEBTOON"
internal const val MANHWA = "MANHWA"
