package eu.kanade.tachiyomi.extension.ja.gorakuweb

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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Element

@Source
abstract class GorakuWeb :
    KeiSource(),
    ConfigurableSource {
    private val preferences by getPreferencesLazy()
    private val rscHeaders get() = headersBuilder()
        .set("rsc", "1")
        .build()

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(ImageInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage {
        val data = client.get(baseUrl, rscHeaders).extractNextJs<List<Entries>>()
        val mangas = data.orEmpty().map { it.toSManga(baseUrl) }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()
        val mangas = document.select("section:has(h2:contains(更新作品)) div.bdr_lg.group").map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .build()

            return client.get(url).toMangasPage()
        }

        val filter = filters.firstInstance<CategoryFilter>()
        if (filter.type == "search") {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("id", filter.value)
                .build()

            return client.get(url).toMangasPage()
        }

        val url = "$baseUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("completed", filter.value)
            .build()

        val data = client.get(url, rscHeaders).extractNextJs<SeriesList>()
        val mangas = data?.cardList?.flatten().orEmpty().map { it.toSManga(baseUrl) }
        return MangasPage(mangas, false)
    }

    private fun Response.toMangasPage(): MangasPage {
        val mangas = this.asJsoup().select("div.bdr_lg.group").map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    private fun Element.toSManga(): SManga = SManga.create().apply {
        url = selectFirst("a")!!.absUrl("href").toHttpUrl().pathSegments[1]
        title = selectFirst("h3")!!.text()
        thumbnail_url = selectFirst("img")?.absUrl("src")
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/episode/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val data = client.get(getMangaUrl(manga), rscHeaders).extractNextJs<EpisodeProps>()!!

        return SMangaUpdate(
            data.toSManga(),
            data.episodeList
                .filter { !hideLocked || !it.isLocked }
                .map { it.toSChapter() },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val data = client.get(getChapterUrl(chapter), rscHeaders).extractNextJs<EpisodeProps>()
            ?: throw Exception("This chapter is not available.")

        return data.metadata.pages.map {
            val url = "${data.base}/${it.filename}".toHttpUrl().newBuilder()
                .encodedQuery("__token__=${data.accessKey}")
                .fragment("${data.keyBytes}:${data.ivBytes}")
                .build()
                .toString()
            Page(it.page, imageUrl = url)
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        CategoryFilter(),
    )

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
