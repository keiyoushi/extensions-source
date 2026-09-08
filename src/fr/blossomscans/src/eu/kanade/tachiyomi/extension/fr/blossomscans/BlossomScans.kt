package eu.kanade.tachiyomi.extension.fr.blossomscans

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getString
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class BlossomScans :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    private val hidePremium: Boolean
        get() = preferences.getBoolean(PREF_HIDE_PREMIUM, true)

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_PREMIUM
            title = "Masquer les chapitres premium"
            summary = "Masquer les chapitres verrouillés en accès anticipé payant"
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = seriesPage(page, sort = "popularity")

    override suspend fun getLatestUpdates(page: Int): MangasPage = seriesPage(page, sort = "recents")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: "popularity"
        val status = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart().orEmpty()
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart().orEmpty()
        return seriesPage(page, query.trim(), sort, status, genres)
    }

    private suspend fun seriesPage(
        page: Int,
        query: String = "",
        sort: String,
        status: String = "",
        genres: String = "",
    ): MangasPage {
        val url = "$baseUrl/api/series".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", PAGE_SIZE.toString())
            addQueryParameter("sort", sort)
            if (query.isNotBlank()) {
                addQueryParameter("search", query)
            }
            if (status.isNotBlank()) {
                addQueryParameter("status", status)
            }
            if (genres.isNotBlank()) {
                addQueryParameter("genres", genres)
            }
        }.build()

        val response = client.get(url).parseAs<SeriesListResponse>()
        val mangas = response.data.map { it.toSManga(baseUrl) }
        return MangasPage(mangas, response.pagination.page < response.pagination.totalPages)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) {
            return null
        }
        if (url.pathSegments.getOrNull(0)?.lowercase() != "serie") {
            return null
        }
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        val manga = SManga.create().apply { this.url = slug }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply { this.url = slug }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/serie/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/serie/${chapter.memo.getString("mangaSlug")}/chapitre/${chapter.memo.getString("number")}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = client.get("$baseUrl/api/series/${manga.url}").parseAs<SeriesDetailsDto>()
        return SMangaUpdate(
            details.toSManga(baseUrl),
            details.chapters.filterNot { it.isLocked && hidePremium }.map { it.toSChapter(details.slug) },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val slug = chapter.memo.getString("mangaSlug")
        val details = client.get("$baseUrl/api/series/$slug").parseAs<SeriesDetailsDto>()
        val entry = details.chapters.firstOrNull { it.chapterId == chapter.url } ?: return emptyList()
        return entry.pageUrls(baseUrl).mapIndexed { index, imageUrl ->
            Page(index, url = getChapterUrl(chapter), imageUrl = imageUrl)
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/api/genres").parseAs()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<String>>().orEmpty()
        return FilterList(
            buildList {
                add(SortFilter())
                add(StatusFilter())
                if (genres.isNotEmpty()) {
                    add(GenreFilter(genres))
                }
            },
        )
    }

    companion object {
        private const val PREF_HIDE_PREMIUM = "pref_hide_premium"
        private const val PAGE_SIZE = 36
    }
}
