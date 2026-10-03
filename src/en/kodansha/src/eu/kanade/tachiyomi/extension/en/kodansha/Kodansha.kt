package eu.kanade.tachiyomi.extension.en.kodansha

import android.content.SharedPreferences
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
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.IOException

@Source
abstract class Kodansha :
    KeiSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(ImageInterceptor())

    override fun Headers.Builder.configureHeaders() = set("azuki-organization-key", ORGANIZATION_KEY)

    override suspend fun getPopularManga(page: Int) = fetchList(page, "", "popular", null, emptyList())

    override suspend fun getLatestUpdates(page: Int) = fetchList(page, "", "recent_series", null, emptyList())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val tags = filters.firstInstance<GenreFilter>().state.filter { it.state }.map { it.value }
        return fetchList(
            page,
            query,
            filters.firstInstance<SortFilter>().value,
            filters.firstInstance<StatusFilter>().value,
            tags,
        )
    }

    private suspend fun fetchList(page: Int, query: String, sort: String, status: String?, tags: List<String>): MangasPage {
        val url = "$API_URL/mangas/v1".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("search_string", query)
            addQueryParameter("sort", sort)
            status?.let { addQueryParameter("release_status", it) }
            if (tags.isNotEmpty()) addQueryParameter("tags", tags.joinToString(","))
            // Novels and print-only series have no readable chapters
            addQueryParameter("series_types", "comic")
            addQueryParameter("format", "digital")
            addQueryParameter("count", PAGE_SIZE.toString())
            addQueryParameter("offset", ((page - 1) * PAGE_SIZE).toString())
        }.build()
        val result = client.get(url).parseAs<SeriesListDto>()
        return MangasPage(result.mangas.map { it.toSManga() }, page * PAGE_SIZE < result.totalCount.toInt())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter(String::isNotBlank)
        val slug = when {
            segments.size >= 2 && segments[0] == "series" -> segments[1]
            segments.size >= 3 && segments[0] == "reader" && segments[1] == "series" -> segments[2]
            else -> return null
        }
        return client.get("$API_URL/manga/slug/$slug/v0").parseAs<SeriesDto>().toSMangaDetails()
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/series/${manga.memo["slug"]!!.stringOrNull}/"

    override fun getChapterUrl(chapter: SChapter): String {
        val slug = chapter.memo["slug"]!!.stringOrNull
        val volume = chapter.memo["volume"]?.stringOrNull ?: return "$baseUrl/series/$slug/"
        return "$baseUrl/reader/series/$slug/volume-$volume/chapter-${chapter.memo["label"]!!.stringOrNull}/"
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (fetchDetails) {
                client.get("$API_URL/manga/${manga.url}/v0").parseAs<SeriesDto>().toSMangaDetails()
            } else {
                manga
            }
        }
        val chapterList = async {
            if (fetchChapters) fetchChapterList(manga) else chapters
        }
        SMangaUpdate(details.await(), chapterList.await())
    }

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> = coroutineScope {
        val slug = manga.memo["slug"]!!.stringOrNull!!
        val volumes = async {
            client.get("$API_URL/mangas/${manga.url}/volumes/v0").parseAs<VolumeListDto>()
                .volumes.associate { it.uuid to it.label }
        }
        val chapterUrl = "$API_URL/mangas/${manga.url}/chapters/v4".toHttpUrl().newBuilder()
            .addQueryParameter("order", "ascending")
            .addQueryParameter("count", "1000")
            .build()
        val result = client.get(chapterUrl).parseAs<ChapterListDto>().chapters
        val volumeLabels = volumes.await()
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        result
            .filter { !hideLocked || it.isFree() }
            .map { it.toSChapter(slug, volumeLabels[it.volumeUuid]) }
            .reversed()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get("$API_URL/chapters/${chapter.url}/pages/v1", ensureSuccess = false)
        if (response.code == 401 || response.code == 403) {
            response.close()
            throw IOException("This chapter must be purchased on Kodansha to read")
        }
        return response.parseAs<PageListDto>().data.pages.mapIndexed { i, page ->
            Page(i, imageUrl = page.image.webp.maxBy { it.width }.url)
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        StatusFilter(),
        GenreFilter(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide paid chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val API_URL = "https://production.api.azuki.co"
        private const val ORGANIZATION_KEY = "fff36c1f-9b3d-418e-b3a9-d2a537ddac06"
        private const val PAGE_SIZE = 24
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
