package eu.kanade.tachiyomi.extension.en.sacachispa

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class Sacachispa :
    KeiSource(),
    ConfigurableSource {

    override val imageFetch = Fetch.IMAGE

    override val supportsFilterFetching = true

    private val preferences by getPreferencesLazy()

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$API_URL/releases".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", MAX_PAGE_SIZE.toString())
            .build()
        val response = client.get(url, Fetch.XHR).parseAs<ReleaseListResponse>()

        // The newest 100 releases, like the site's homepage; later pages only repeat these series.
        return MangasPage(response.data.map { it.manga.toSManga() }.distinctBy { it.url }, hasNextPage = false)
    }

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val genres = filters.firstInstanceOrNull<GenreFilter>()
        val included = genres?.included.orEmpty()
        val excluded = genres?.excluded.orEmpty()
        val commissions = filters.firstInstanceOrNull<CatalogFilter>()?.commissions == true

        // Genres aren't an API parameter: the site's catalogue filters a full page itself.
        val pageSize = if (included.isEmpty() && excluded.isEmpty()) PAGE_SIZE else MAX_PAGE_SIZE

        val url = "$API_URL/manga".toHttpUrl().newBuilder()
            .apply {
                if (query.isNotBlank()) {
                    addPathSegment("search")
                    addQueryParameter("q", query)
                } else if (commissions) {
                    addQueryParameter("is_commission", "true")
                }
            }
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", pageSize.toString())
            .build()

        val response = client.get(url, Fetch.XHR).parseAs<MangaListResponse>()

        return MangasPage(
            mangas = response.data
                .filter { it.genres.containsAll(included) && it.genres.none(excluded::contains) }
                .map { it.toSManga() },
            hasNextPage = response.pagination.page < response.pagination.pages,
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val id = url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        val mangaUrl = when (url.pathSegments.first()) {
            "manga" -> id
            "read" -> client.get("$API_URL/releases/$id", Fetch.XHR).parseAs<Data<ReleaseDto>>().data.manga.id
            else -> return null
        }
        val manga = SManga.create().apply { this.url = mangaUrl }

        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // The slug segment is cosmetic, so the id stands in for it until details have loaded.
    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}/${manga.memo.getStringOrNull("slug") ?: manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = if (fetchDetails) async { fetchMangaDetails(manga).toSManga(manga.url) } else null
        val chapterList = if (fetchChapters) async { fetchChapterList(manga) } else null

        SMangaUpdate(
            manga = details?.await() ?: manga,
            chapters = chapterList?.await() ?: chapters,
        )
    }

    private suspend fun fetchMangaDetails(manga: SManga): MangaDetailDto = client.get("$API_URL/manga/${manga.url}", Fetch.XHR)
        .parseAs<Data<MangaDetailDto>>().data

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/read/${chapter.url}"

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        // Entries from before the site rewrite store the slug, the releases endpoint takes the id.
        val mangaId = manga.url.takeIf { UUID_REGEX.matches(it) }
            ?: manga.memo.getStringOrNull("id")
            ?: fetchMangaDetails(manga).id

        val hideLocked = preferences.getBoolean(HIDE_LOCKED_CHAPTERS, true)
        val chapters = mutableListOf<SChapter>()
        var page = 1

        do {
            val url = "$API_URL/releases".toHttpUrl().newBuilder()
                .addQueryParameter("mangaId", mangaId)
                .addQueryParameter("page", page.toString())
                .addQueryParameter("limit", CHAPTER_PAGE_SIZE.toString())
                .build()
            val response = client.get(url, Fetch.XHR).parseAs<ReleaseListResponse>()

            chapters += response.data.mapNotNull { it.toSChapter(hideLocked) }
        } while (page++ < response.pagination.pages)

        return chapters.sortedByDescending { it.chapter_number }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get("$API_URL/releases/${chapter.url}/pages", Fetch.XHR, ensureSuccess = false)

        // Patreon-exclusive chapters answer 403 with a message for the reader.
        if (response.code == 403 && response.header("Content-Type").orEmpty().startsWith("application/json")) {
            throw Exception(response.parseAs<ErrorResponse>().error.message)
        }
        if (!response.isSuccessful) {
            response.close()
            throw HttpException(response.code)
        }

        return response.parseAs<Data<PageListDto>>().data.items
            .mapIndexed { index, item -> Page(index, imageUrl = item.url) }
    }

    override suspend fun fetchFilterData(): JsonElement = client.get("$API_URL/manga/genres", Fetch.XHR).parseAs()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<Data<List<NamedDto>>>()?.data.orEmpty()

        return FilterList(
            buildList {
                add(Filter.Header("Catalog applies without a text search"))
                add(CatalogFilter())
                if (genres.isNotEmpty()) add(GenreFilter(genres.map { it.name }))
            },
        )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_CHAPTERS
            title = "Hide Patreon-exclusive chapters"
            summaryOn = "Chapters will be hidden"
            summaryOff = "Chapters will be marked with an icon: 🔒"
            setDefaultValue(true)
        }.let(screen::addPreference)
    }

    private companion object {
        const val API_URL = "https://api.sacachispa.site/api"
        const val HIDE_LOCKED_CHAPTERS = "hide_patreon_chapters"
        const val PAGE_SIZE = 24
        const val MAX_PAGE_SIZE = 100
        const val CHAPTER_PAGE_SIZE = 500
        val UUID_REGEX = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
