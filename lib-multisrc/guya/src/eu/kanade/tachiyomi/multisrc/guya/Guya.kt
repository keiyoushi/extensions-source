package eu.kanade.tachiyomi.multisrc.guya

import android.content.SharedPreferences
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.select.Evaluator

abstract class Guya :
    KeiSource(),
    ConfigurableSource {

    override fun Headers.Builder.configureHeaders(): Headers.Builder = set(
        "User-Agent",
        "(Android ${Build.VERSION.RELEASE}; " +
            "${Build.MANUFACTURER} ${Build.MODEL}) " +
            "Tachiyomi/${AppInfo.getVersionName()} ${Build.ID}",
    )

    // Preferences configuration
    private val preferences: SharedPreferences by getPreferencesLazy()

    // Scanlator id -> name, loaded while browsing so the preference screen can list them
    private var scanlators = emptyMap<String, String>()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var scanlatorsJob: Job? = null

    private fun updateScanlators() {
        if (scanlators.isEmpty() && scanlatorsJob?.isActive != true) {
            scanlatorsJob = scope.launch {
                scanlators = runCatching { client.get("$baseUrl/api/get_all_groups/").parseAs<Map<String, String>>() }
                    .getOrDefault(emptyMap())
            }
        }
    }

    private suspend fun fetchAllSeries(): Map<String, SeriesDto> {
        updateScanlators()
        return client.get("$baseUrl/api/get_all_series/").parseAs()
    }

    private suspend fun fetchSeries(slug: String): SeriesDetailsDto = client.get("$baseUrl/api/series/$slug/").parseAs()

    // Allows sources to limit which series are shown
    protected open fun filterMangas(mangasPage: MangasPage): MangasPage = mangasPage

    override suspend fun getPopularManga(page: Int): MangasPage = filterMangas(parseManga(fetchAllSeries()))

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val mangas = fetchAllSeries().entries
            .sortedByDescending { it.value.lastUpdated }
            .map { (title, series) -> series.toSManga(title) }

        return filterMangas(MangasPage(mangas, false))
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val series = fetchAllSeries()
        val results = if (query.startsWith(SLUG_PREFIX)) {
            val slug = query.removePrefix(SLUG_PREFIX)
            series.filterValues { it.slug == slug }
        } else {
            series.filterKeys { it.contains(query, ignoreCase = true) }
        }

        return filterMangas(parseManga(results))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.getOrNull(2) ?: return null

        return fetchSeries(slug).toSManga()
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/reader/series/${manga.url}/"

    // Details and chapters come from the same response
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val series = fetchSeries(manga.url)
        return SMangaUpdate(series.toSManga().apply { title = manga.title }, parseChapterList(series, manga))
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/read/manga/${chapter.url.replace('.', '-')}/1/"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val series = fetchSeries(chapter.url.split("/")[0])
        val chapterNum = chapter.name.split(" - ")[0]
        val chapterDto = series.chapters.getValue(chapterNum)
        val scanlator = series.groups.entries.firstOrNull { it.value == chapter.scanlator }?.key
            ?: chapter.scanlator.orEmpty()

        return chapterDto.groups.getValue(scanlator).mapIndexed { i, filename ->
            Page(i + 1, imageUrl = pageBuilder(series.slug, chapterDto.folder, filename, scanlator))
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val scanlatorKeys = scanlators.keys.toTypedArray()

        val preference = ListPreference(screen.context).apply {
            key = SCANLATOR_PREFERENCE
            title = "Preferred scanlator"
            entries = Array(scanlatorKeys.size) { scanlators.getValue(scanlatorKeys[it]) }
            entryValues = scanlatorKeys
            summary = "Current: %s\n\n" +
                "This setting sets the scanlation group to prioritize " +
                "on chapter refresh/update. It will get the next available if " +
                "your preferred scanlator isn't an option (yet)."

            setDefaultValue("1")
        }

        screen.addPreference(preference)
    }

    // ------------- Helpers and whatnot ---------------

    private fun parseChapterList(series: SeriesDetailsDto, manga: SManga): List<SChapter> {
        val chapterList = mutableListOf<SChapter>()

        for ((chapterNum, chapterDto) in series.chapters) {
            val sort = chapterDto.preferredSort ?: series.preferredSort
            if (sort != null) {
                chapterList.add(parseChapterFromJson(chapterDto, chapterNum, sort, series))
            } else {
                for (groupNum in chapterDto.groups.keys) {
                    val chapter = SChapter.create()

                    chapter.scanlator = series.groups[groupNum]
                    chapterDto.releaseDate?.get(groupNum)?.let {
                        chapter.date_upload = it * 1000
                    }
                    chapter.name = chapterNum + " - " + chapterDto.title
                    chapter.chapter_number = chapterNum.toFloat()
                    chapter.url = "${manga.url}/$chapterNum"
                    chapterList.add(chapter)
                }
            }
        }

        return chapterList.reversed()
    }

    // Helper function to get all the listings
    private fun parseManga(payload: Map<String, SeriesDto>): MangasPage = MangasPage(payload.map { (title, series) -> series.toSManga(title) }, false)

    private fun SeriesDto.toSManga(title: String): SManga = SManga.create().also {
        it.title = title
        it.artist = artist
        it.author = author
        it.description = description?.cleanDescription()
        it.url = slug
        it.thumbnail_url = cover?.toCoverUrl()
    }

    private fun SeriesDetailsDto.toSManga(): SManga = SManga.create().also {
        it.title = title
        it.artist = artist
        it.author = author
        it.description = description?.cleanDescription()
        it.url = slug
        it.thumbnail_url = cover?.toCoverUrl()
    }

    private fun String.cleanDescription(): String {
        if ('<' !in this) return this // no HTML
        return Jsoup.parseBodyFragment(this).body().run {
            select(Evaluator.Tag("a")).remove()
            text()
        }
    }

    private fun String.toCoverUrl(): String? = when {
        startsWith("http") -> this
        isNotEmpty() -> "$baseUrl/$this"
        else -> null
    }

    private fun parseChapterFromJson(json: ChapterDto, num: String, sort: List<String>, series: SeriesDetailsDto): SChapter {
        val chapter = SChapter.create()

        // Get the scanlator info based on group ranking; do it first since we need it later
        val firstGroupId = getBestScanlator(json.groups.keys, sort)
        chapter.scanlator = series.groups[firstGroupId] ?: firstGroupId
        chapter.date_upload = json.releaseDate!!.getValue(firstGroupId) * 1000
        chapter.name = num + " - " + json.title
        chapter.chapter_number = num.toFloat()
        chapter.url = "${series.slug}/$num"

        return chapter
    }

    private fun getBestScanlator(groups: Set<String>, sort: List<String>): String {
        val preferred = preferences.getString(SCANLATOR_PREFERENCE, null)

        if (preferred != null && preferred in groups) {
            return preferred
        }
        // If all fails, fall-back to the next available key
        return sort.firstOrNull { it in groups } ?: groups.first()
    }

    private fun pageBuilder(slug: String, folder: String, filename: String, groupId: String): String = "$baseUrl/media/manga/$slug/chapters/$folder/$groupId/$filename"

    companion object {
        const val SLUG_PREFIX = "slug:"
        private const val SCANLATOR_PREFERENCE = "SCANLATOR_PREFERENCE"
    }
}
