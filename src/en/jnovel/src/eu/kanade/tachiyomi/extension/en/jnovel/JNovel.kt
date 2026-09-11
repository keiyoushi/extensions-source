package eu.kanade.tachiyomi.extension.en.jnovel

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
import keiyoushi.lib.e4p.E4PInterceptor
import keiyoushi.lib.e4p.E4PManifestReader
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

@Source
abstract class JNovel :
    KeiSource(),
    ConfigurableSource {
    override val supportsLatest = false

    private val domain get() = baseUrl.toHttpUrl().host
    private val viewerUrl get() = "https://labs.$domain/embed/v2"
    private val preferences by getPreferencesLazy()
    private val manifestReader get() = E4PManifestReader(client, headers)
    private val rscHeaders get() = headersBuilder()
        .set("rsc", "1")
        .build()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(E4PInterceptor())
        addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (!response.isSuccessful && response.request.url.toString().startsWith(viewerUrl)) {
                throw IOException("Log in via WebView and purchase this chapter to read.")
            }
            response
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("type", "manga")
            .addQueryParameter("page", page.toString())
            .build()

        return client.get(url, rscHeaders).toMangasPage()
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/series".toHttpUrl().newBuilder().apply {
            addQueryParameter("type", "manga")
            if (query.isNotBlank()) addQueryParameter("search", query)
            addFilter("sort", filters.firstInstance<SortFilter>())
            addFilter("label", filters.firstInstance<LabelFilter>())
            addFilter("status", filters.firstInstance<StatusFilter>())
            addFilter("rentals", filters.firstInstance<RentalFilter>())
        }.build()

        return client.get(url, rscHeaders).toMangasPage()
    }

    private fun Response.toMangasPage(): MangasPage {
        val result = this.extractNextJs<SeriesResponse>()
        val mangas = result?.seriesList?.series.orEmpty().map { it.toSManga() }
        return MangasPage(mangas, result?.seriesList?.hasNextPage() ?: false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val result = requireNotNull(client.get(getMangaUrl(manga), rscHeaders).extractNextJs<SeriesDetailsResponse>())
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chapterList = result.volumes.flatMap { volume ->
            val owned = volume.volume?.owned == true
            volume.parts
                .filter { !hideLocked || !it.isLocked(owned) }
                .map { it.toSChapter(result.series.title, owned) }
        }.reversed()

        val creators = result.volumes.firstOrNull()?.volume?.creators.orEmpty()
        return SMangaUpdate(
            result.series.toSManga(creators),
            chapterList,
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val embedUrl = document.selectFirst("iframe[src^='$viewerUrl']")?.absUrl("src")
            ?: throw Exception("Log in via WebView and purchase this chapter to read.")
        val manifestUrl = client.get("$embedUrl/info.json").parseAs<Manifest>().e4pManifest
        return manifestReader.extractPagesFromEncryptedManifest(manifestUrl.toHttpUrl())
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/read/${chapter.url}"

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        LabelFilter(),
        StatusFilter(),
        RentalFilter(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
