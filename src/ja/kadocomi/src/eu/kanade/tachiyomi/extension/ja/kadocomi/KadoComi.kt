package eu.kanade.tachiyomi.extension.ja.kadocomi

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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Source
abstract class KadoComi :
    KeiSource(),
    ConfigurableSource {
    private val preferences by getPreferencesLazy()
    private val apiUrl get() = "$baseUrl/api"
    private val pageLimit = 30

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(ImageInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage {
        val offset = (page - 1) * pageLimit
        val url = "$apiUrl/ranking".toHttpUrl().newBuilder()
            .addQueryParameter("limit", pageLimit.toString())
            .addQueryParameter("offset", offset.toString())
            .build()

        return client.get(url).toMangasPage(offset)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val offset = (page - 1) * pageLimit
        val url = "$apiUrl/series/new".toHttpUrl().newBuilder()
            .addQueryParameter("limit", pageLimit.toString())
            .addQueryParameter("offset", offset.toString())
            .build()

        return client.get(url).toMangasPage(offset)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstance<SortFilter>().value
        val offset = (page - 1) * pageLimit
        val url = "$apiUrl/search/keywords".toHttpUrl().newBuilder()
            .addQueryParameter("keywords", query)
            .addQueryParameter("limit", pageLimit.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("sortBy", sort)
            .build()

        return client.get(url).toMangasPage(offset)
    }

    private fun Response.toMangasPage(offset: Int): MangasPage {
        val result = this.parseAs<SeriesResponse>()
        val mangas = result.result.map { it.toSManga() }
        return MangasPage(mangas, result.hasNextPage(offset, pageLimit))
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Note: Search and filters are applied together"),
        SortFilter(),
    )

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val url = "$apiUrl/contents/details/work".toHttpUrl().newBuilder()
            .addQueryParameter("workCode", manga.url.substringAfterLast("/"))
            .build()

        val result = client.get(url).parseAs<DetailsResponse>()
        return SMangaUpdate(
            result.work.toSManga(),
            result.latestEpisodes.result
                .filter { !hideLocked || it.isActive }
                .map { it.toSChapter(result.work.code) },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$apiUrl/contents/viewer".toHttpUrl().newBuilder()
            .addQueryParameter("episodeId", chapter.url)
            .addQueryParameter("imageSizeType", IMAGE_SIZE)
            .build()

        val manuscript = client.get(url).parseAs<ViewerResponse>().manuscripts
        if (manuscript.isEmpty()) throw Exception("このチャプターは非公開です\nChapter is not available!")
        return manuscript.mapIndexed { index, pages ->
            Page(index, imageUrl = "${pages.drmImageUrl}#${pages.drmHash}")
        }
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/detail/${chapter.memo["workCode"]!!.string}/episodes/${chapter.memo["episodeCode"]!!.string}"

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Unavailable Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        private const val IMAGE_SIZE = "width:1284"
    }
}
