package eu.kanade.tachiyomi.multisrc.bakkin

import android.os.Build
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

abstract class BakkinReaderX :
    KeiSource(),
    ConfigurableSource {

    override val supportsLatest = false

    private val userAgent = "Mozilla/5.0 (" +
        "Android ${Build.VERSION.RELEASE}; Mobile) " +
        "Tachiyomi/${AppInfo.getVersionName()}"

    protected val preferences by getPreferencesLazy()

    private val mainUrl: String
        get() = baseUrl + "main.php" + preferences.getString("quality", "")

    override fun Headers.Builder.configureHeaders(): Headers.Builder = set("User-Agent", userAgent)

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val matches = fetchAllSeries().filter { series ->
            query.isBlank() || series.toString().contains(query, ignoreCase = true)
        }

        return MangasPage(matches.map { it.toSManga() }, hasNextPage = false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        // Reader links look like "<baseUrl>#m=<series>&v=<volume>&c=<chapter>"
        val seriesDir = url.fragment
            ?.split('&')
            ?.firstOrNull { it.startsWith("m=") }
            ?.substringAfter("m=")
            ?: return null

        return fetchAllSeries()
            .firstOrNull { it.dir == seriesDir }
            ?.toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val series = fetchSeries(manga.url)

        val chapterList = series.map { chapter ->
            SChapter.create().apply {
                url = chapter.dir
                name = chapter.toString()
                chapter_number = chapter.number
            }
        }.reversed()

        return SMangaUpdate(series.toSManga(), chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val seriesDir = chapter.url.substringBefore('/')
        val pages = fetchSeries(seriesDir).first { it.dir == chapter.url }

        return pages.mapIndexed { index, path ->
            Page(index, imageUrl = baseUrl + path)
        }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl#m=${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String {
        val (m, v, c) = chapter.url.split('/')
        return "$baseUrl#m=$m&v=$v&c=$c"
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = "quality"
            summary = "%s"
            title = "Image quality"
            entries = arrayOf("Original", "Compressed")
            entryValues = arrayOf("?fullsize", "")
            setDefaultValue("")
        }.let(screen::addPreference)
    }

    private var seriesCache = emptyList<Series>()

    private suspend fun fetchAllSeries(): List<Series> {
        if (seriesCache.isEmpty()) {
            seriesCache = client.get(mainUrl)
                .parseAs<Map<String, Series>>()
                .values
                .toList()
        }
        return seriesCache
    }

    private suspend fun fetchSeries(dir: String): Series = fetchAllSeries().first { it.dir == dir }

    private fun Series.toSManga() = SManga.create().apply {
        url = dir
        title = this@toSManga.toString()
        thumbnail_url = baseUrl + cover
        author = this@toSManga.author
        status = when (this@toSManga.status) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
}
