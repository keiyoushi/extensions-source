package eu.kanade.tachiyomi.multisrc.bakkin

import android.os.Build
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
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

abstract class BakkinReaderX :
    KeiSource(),
    ConfigurableSource {

    override val supportsLatest = false

    private val userAgent = "Mozilla/5.0 (" +
        "Android ${Build.VERSION.RELEASE}; Mobile) " +
        "Keiyoushi/$versionId"

    protected val preferences by getPreferencesLazy()

    private val mainUrl: String
        get() = baseUrl + "main.php" + preferences.getString("quality", "")

    private var seriesCache = emptyList<Series>()

    private suspend fun <R> withSeries(block: (List<Series>) -> R): R {
        if (seriesCache.isEmpty()) {
            val response = client.get(mainUrl, headers)
            if (!response.isSuccessful) throw Exception("HTTP error ${response.code}")
            seriesCache = response.parseAs<Map<String, Series>>().values.toList()
        }
        return block(seriesCache)
    }

    private fun List<Series>.search(query: String) = if (query.isBlank()) this else filter { it.toString().contains(query, true) }

    override fun Headers.Builder.configureHeaders() = add("User-Agent", userAgent)

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int) = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = withSeries { series ->
        val mangas = series.search(query).map {
            SManga.create().apply {
                url = it.dir
                title = it.toString()
                thumbnail_url = baseUrl + it.cover
            }
        }
        MangasPage(mangas, false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = withSeries { series ->
        val seriesEntry = series.first { it.dir == manga.url }

        val sManga = SManga.create().apply {
            url = seriesEntry.dir
            title = seriesEntry.toString()
            thumbnail_url = baseUrl + seriesEntry.cover
            initialized = true
            author = seriesEntry.author
            status = when (seriesEntry.status) {
                "Ongoing" -> SManga.ONGOING
                "Completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }

        val sChapters = seriesEntry.map { chapter ->
            SChapter.create().apply {
                url = chapter.dir
                name = chapter.toString()
                chapter_number = chapter.number
                date_upload = 0L
            }
        }.reversed()

        SMangaUpdate(sManga, sChapters)
    }

    override suspend fun getPageList(chapter: SChapter) = withSeries { series ->
        series.flatten().first { it.dir == chapter.url }
            .mapIndexed { idx, page -> Page(idx, "", baseUrl + page) }
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

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(key, newValue as String).commit()
            }
        }.let(screen::addPreference)
    }
}
