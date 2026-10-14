package eu.kanade.tachiyomi.extension.es.insanosscan

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
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import java.lang.UnsupportedOperationException

@Source
abstract class InsanosScan :
    KeiSource(),
    ConfigurableSource {

    override val supportsLatest = false

    private val preferences = getPreferences()

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int) = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ) = client.get("$baseUrl/series").parseAs<List<Series>>().map {
        it.toSManga(baseUrl)
    }.filter { query in it.title }
        .let { MangasPage(it, false) }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/serie/${manga.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val seriesId = url.pathSegments.getOrNull(1) ?: return null
        seriesId.toIntOrNull() ?: return null

        return fetchMangaUpdate(
            SManga.create().apply { this.url = seriesId },
            emptyList(),
            true,
            false,
        ).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ) = coroutineScope {
        val updatedManga = async {
            if (fetchDetails) {
                client.get("$baseUrl/series/${manga.url}")
                    .parseAs<Series>().toSManga(baseUrl)
            } else {
                manga
            }
        }

        val updatedChapters = async {
            if (fetchChapters) {
                client.get("$baseUrl/series/${manga.url}/chapters")
                    .parseAs<List<Chapter>>().mapNotNull {
                        it.toSChapter(showPaidChapters)
                    }.reversed()
            } else {
                chapters
            }
        }

        SMangaUpdate(updatedManga.await(), updatedChapters.await())
    }

    override fun getChapterUrl(chapter: SChapter) = chapter.url.split("/").let { (seriesId, cid) ->
        "$baseUrl/reader?series=$seriesId&chapter=$cid"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (seriesId, cid) = chapter.url.split("/")

        return client.get(
            "$baseUrl/series/$seriesId/chapters/$cid/pages",
        ).parseAs<Pages>().pages.mapIndexed { i, url ->
            val imageUrl = if (url.startsWith("/")) baseUrl + url else url
            Page(i, imageUrl = imageUrl)
        }
    }

    private val showPaidChapters: Boolean
        get() = preferences.getBoolean(PREF_SHOW_PAID, false)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_PAID
            title = "Mostrar capítulos de pago"
            summary = "Incluye capítulos que requieren monedas para leer"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val PREF_SHOW_PAID = "show_paid_chapters"
    }
}
