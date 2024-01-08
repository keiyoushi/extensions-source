package eu.kanade.tachiyomi.multisrc.bakkin

import android.app.Application
import android.os.Build
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.Headers
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

abstract class BakkinReaderX(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : ConfigurableSource, HttpSource() {
    override val supportsLatest = false

    private val userAgent = "Mozilla/5.0 (" +
        "Android ${Build.VERSION.RELEASE}; Mobile) " +
        "Tachiyomi/${AppInfo.getVersionName()}"

    protected val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)!!
    }

    private val json by lazy { Injekt.get<Json>() }

    private val mainUrl: String
        get() = baseUrl + "main.php" + preferences.getString("quality", "")

    private var seriesCache = emptyList<Series>()

    private fun <R> observableSeries(block: (List<Series>) -> R) =
        if (seriesCache.isNotEmpty()) {
            rx.Observable.just(block(seriesCache))!!
        } else {
            client.newCall(GET(mainUrl, headers)).asObservableSuccess().map {
                seriesCache = json.parseToJsonElement(it.body.string())
                    .jsonObject.values.map(json::decodeFromJsonElement)
                block(seriesCache)
            }!!
        }

    private fun List<Series>.search(query: String) =
        if (query.isBlank()) this else filter { it.toString().contains(query, true) }

    override fun headersBuilder() =
        Headers.Builder().add("User-Agent", userAgent)

    override fun fetchPopularManga(page: Int) =
        fetchSearchManga(page, "", FilterList())

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) =
        observableSeries { series ->
            series.search(query).map {
                SManga.create().apply {
                    url = it.dir
                    title = it.toString()
                    thumbnail_url = baseUrl + it.cover
                }
            }.let { MangasPage(it, false) }
        }

    override fun fetchMangaDetails(manga: SManga) =
        observableSeries { series ->
            series.first { it.dir == manga.url }.let {
                SManga.create().apply {
                    url = it.dir
                    title = it.toString()
                    thumbnail_url = baseUrl + it.cover
                    initialized = true
                    author = it.author
                    status = when (it.status) {
                        "Ongoing" -> SManga.ONGOING
                        "Completed" -> SManga.COMPLETED
                        else -> SManga.UNKNOWN
                    }
                }
            }
        }

    override fun fetchChapterList(manga: SManga) =
        observableSeries { series ->
            series.first { it.dir == manga.url }.map { chapter ->
                SChapter.create().apply {
                    url = chapter.dir
                    name = chapter.toString()
                    chapter_number = chapter.number
                    date_upload = 0L
                }
            }.reversed()
        }

    override fun fetchPageList(chapter: SChapter) =
        observableSeries { series ->
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

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        throw UnsupportedOperationException("Not used!")

    override fun popularMangaRequest(page: Int) =
        throw UnsupportedOperationException("Not used!")

    override fun latestUpdatesRequest(page: Int) =
        throw UnsupportedOperationException("Not used!")

    override fun mangaDetailsRequest(manga: SManga) =
        throw UnsupportedOperationException("Not used!")

    override fun searchMangaParse(response: Response) =
        throw UnsupportedOperationException("Not used!")

    override fun popularMangaParse(response: Response) =
        throw UnsupportedOperationException("Not used!")

    override fun latestUpdatesParse(response: Response) =
        throw UnsupportedOperationException("Not used!")

    override fun mangaDetailsParse(response: Response) =
        throw UnsupportedOperationException("Not used!")

    override fun chapterListParse(response: Response) =
        throw UnsupportedOperationException("Not used!")

    override fun pageListParse(response: Response) =
        throw UnsupportedOperationException("Not used!")

    override fun imageUrlParse(response: Response) =
        throw UnsupportedOperationException("Not used!")
}
