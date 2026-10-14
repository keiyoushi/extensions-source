package eu.kanade.tachiyomi.extension.en.nuviatoon

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class NuviaToon :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    override fun Headers.Builder.configureHeaders() = add("Accept", "application/json")

    override suspend fun getPopularManga(page: Int): MangasPage = fetchComics(page, query = null, sort = "popular")

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchComics(page, query = null, sort = "newest")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = fetchComics(page, query, sort = filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: "popular", filters = filters)

    private suspend fun fetchComics(page: Int, query: String?, sort: String, filters: FilterList = FilterList()): MangasPage {
        val url = "$baseUrl/api/comics".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("pageSize", "18")
            if (!query.isNullOrEmpty()) addQueryParameter("q", query)
            filters.firstInstanceOrNull<StatusFilter>()?.toUriPart()?.let { addQueryParameter("status", it) }
            filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()?.let { addQueryParameter("genre", it) }
            filters.firstInstanceOrNull<TypeFilter>()?.toUriPart()?.let { addQueryParameter("type", it) }
            filters.firstInstanceOrNull<RatingFilter>()?.toUriPart()?.let { addQueryParameter("minRating", it) }
            addQueryParameter("sort", sort)
        }.build()

        return client.get(url).parseAs<ComicsResponse>().toMangasPage()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "comics") return null
        val slug = url.pathSegments.getOrNull(1) ?: return null

        val manga = SManga.create().apply { this.url = slug }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/comics/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/comics/${chapter.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val detail = client.get("$baseUrl/api/comics/${manga.url}").parseAs<DetailResponse>().data
        return SMangaUpdate(detail.toSManga(), detail.toSChapterList(preferences.hideLockedChapters()))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val id = chapter.memo["chapterId"]?.stringOrNull ?: return emptyList()
        val response = client.get("$baseUrl/api/comic-chapters/$id/content", ensureSuccess = false)
        if (!response.isSuccessful) {
            response.close()
            throw when (response.code) {
                402 -> Exception("Log in via WebView and unlock this chapter with coins to read.")
                else -> HttpException(response.code)
            }
        }
        return response.parseAs<ContentResponse>().data.toPageList()
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        StatusFilter(),
        GenreFilter(),
        TypeFilter(),
        RatingFilter(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_LOCKED_CHAPTERS
            title = "Hide locked chapters"
            summary = "Hides chapters that require coins to unlock"
            setDefaultValue(true)
        }.let(screen::addPreference)
    }

    private fun SharedPreferences.hideLockedChapters(): Boolean = getBoolean(
        PREF_HIDE_LOCKED_CHAPTERS,
        true,
    )
}

private const val PREF_HIDE_LOCKED_CHAPTERS = "pref_hide_locked_chapters"
