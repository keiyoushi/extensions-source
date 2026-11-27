package eu.kanade.tachiyomi.extension.en.arvenscansnet

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class ArvenScans :
    KeiSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val apiUrl get() = "https://api.${baseUrl.toHttpUrl().host}"

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/works/most-read".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "20")
            .build()
        val works = client.get(url, Fetch.XHR).parseAs<DataDto<List<WorkDto>>>().data
        return MangasPage(works.map { it.toSManga(apiUrl) }, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter()))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.slug
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            if (query.isBlank() && genre != null) {
                addPathSegments("genres/$genre/works")
            } else {
                addPathSegment("works")
                filters.firstInstanceOrNull<SortFilter>()?.sort?.let { addQueryParameter("sort", it) }
                if (query.isNotBlank()) addQueryParameter("search", query.trim())
            }
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "24")
        }.build()
        val data = client.get(url, Fetch.XHR).parseAs<PagedDto<WorkDto>>()
        return MangasPage(data.data.map { it.toSManga(apiUrl) }, data.hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        val workId = when (segments.firstOrNull()) {
            "series" -> {
                val slug = segments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
                val resolveUrl = apiUrl.toHttpUrl().newBuilder()
                    .addPathSegments("resolve/work")
                    .addPathSegment(slug)
                    .build()
                client.get(resolveUrl, Fetch.XHR).parseAs<WorkIdDto>().workId
            }
            "reader" -> {
                val chapterId = segments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
                val chapterUrl = apiUrl.toHttpUrl().newBuilder()
                    .addPathSegment("chapters")
                    .addPathSegment(chapterId)
                    .build()
                client.get(chapterUrl, Fetch.XHR).parseAs<WorkIdDto>().workId
            }
            else -> return null
        }
        val manga = SManga.create().apply { this.url = workId }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.memo["slug"]?.stringOrNull ?: manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = if (fetchDetails) {
            async {
                val genres = async { client.get("$apiUrl/works/${manga.url}/genres", Fetch.XHR).parseAs<DataDto<List<GenreDto>>>().data }
                val work = client.get("$apiUrl/works/${manga.url}", Fetch.XHR).parseAs<WorkDto>()
                work.toSManga(apiUrl, genres.await())
            }
        } else {
            null
        }
        val chapterList = if (fetchChapters) async { fetchChapters(manga.url) } else null
        SMangaUpdate(details?.await() ?: manga, chapterList?.await() ?: chapters)
    }

    private suspend fun fetchChapters(workId: String): List<SChapter> = coroutineScope {
        fun pageUrl(page: Int) = "$apiUrl/works/$workId/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "100")
            .addQueryParameter("order", "desc")
            .build()
        val first = client.get(pageUrl(1), Fetch.XHR).parseAs<PagedDto<ChapterDto>>()
        val rest = (2..first.totalPages).map { page ->
            async { client.get(pageUrl(page), Fetch.XHR).parseAs<PagedDto<ChapterDto>>().data }
        }.awaitAll()
        val showPaid = preferences.getBoolean(SHOW_PAID_PREF, false)
        (first.data + rest.flatten())
            .filter { showPaid || it.coins == 0 }
            .map { it.toSChapter() }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val images = client.get("$apiUrl/chapters/${chapter.url}", Fetch.XHR).parseAs<ChapterPagesDto>().images
        return images.mapIndexed { index, path -> Page(index, imageUrl = apiUrl + path) }
    }

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/genres", Fetch.XHR).parseAs<DataDto<List<GenreDto>>>().data
        .filter { (it.workCount ?: 0) > 0 }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreDto>>() ?: return FilterList(SortFilter())
        return FilterList(
            SortFilter(),
            Filter.Separator(),
            Filter.Header("Genre is ignored when searching, and Sort when a genre is set"),
            GenreFilter(genres),
        )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_PAID_PREF
            title = "Show paid chapters"
            summary = "Chapters that cost points on the site are listed with 🔒. They can't be read here."
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val SHOW_PAID_PREF = "show_paid_chapters"
    }
}
