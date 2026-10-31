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
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
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

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$API_URL/works/most-read".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "20")
            .build()
        val works = client.get(url).parseAs<DataDto<List<WorkDto>>>().data
        return MangasPage(works.map { it.toSManga(API_URL) }, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter()))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.slug
        val url = API_URL.toHttpUrl().newBuilder().apply {
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
        val data = client.get(url).parseAs<PagedDto<WorkDto>>()
        return MangasPage(data.data.map { it.toSManga(API_URL) }, data.hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        val workId = when (segments.firstOrNull()) {
            "series" -> {
                val slug = segments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
                val resolveUrl = API_URL.toHttpUrl().newBuilder()
                    .addPathSegments("resolve/work")
                    .addPathSegment(slug)
                    .build()
                client.get(resolveUrl).parseAs<WorkIdDto>().workId
            }
            "reader" -> {
                val chapterId = segments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
                val chapterUrl = API_URL.toHttpUrl().newBuilder()
                    .addPathSegment("chapters")
                    .addPathSegment(chapterId)
                    .build()
                client.get(chapterUrl).parseAs<WorkIdDto>().workId
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
                val genres = async { client.get("$API_URL/works/${manga.url}/genres").parseAs<DataDto<List<GenreDto>>>().data }
                val work = client.get("$API_URL/works/${manga.url}").parseAs<WorkDto>()
                work.toSManga(API_URL, genres.await())
            }
        } else {
            null
        }
        val chapterList = if (fetchChapters) async { fetchChapters(manga.url) } else null
        SMangaUpdate(details?.await() ?: manga, chapterList?.await() ?: chapters)
    }

    private suspend fun fetchChapters(workId: String): List<SChapter> = coroutineScope {
        fun pageUrl(page: Int) = "$API_URL/works/$workId/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "100")
            .addQueryParameter("order", "desc")
            .build()
        val first = client.get(pageUrl(1)).parseAs<PagedDto<ChapterDto>>()
        val rest = (2..first.totalPages).map { page ->
            async { client.get(pageUrl(page)).parseAs<PagedDto<ChapterDto>>().data }
        }.awaitAll()
        val showPaid = preferences.getBoolean(SHOW_PAID_PREF, false)
        (first.data + rest.flatten())
            .filter { showPaid || it.coins == 0 }
            .map { it.toSChapter() }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val images = client.get("$API_URL/chapters/${chapter.url}").parseAs<ChapterPagesDto>().images
        return images.mapIndexed { index, path -> Page(index, imageUrl = API_URL + path) }
    }

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$API_URL/genres").parseAs<DataDto<List<GenreDto>>>().data
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
        private const val API_URL = "https://api.arvenscans.net"
        private const val SHOW_PAID_PREF = "show_paid_chapters"
    }
}
