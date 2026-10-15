package eu.kanade.tachiyomi.extension.all.cubari

import android.os.Build
import android.util.Base64
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.double
import okhttp3.Headers
import okhttp3.HttpUrl
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Cubari : KeiSource() {

    override fun Headers.Builder.configureHeaders() = set(
        "User-Agent",
        "(Android ${Build.VERSION.RELEASE}; " +
            "${Build.MANUFACTURER} ${Build.MODEL}) " +
            "Tachiyomi/${AppInfo.getVersionName()} ${Build.ID} " +
            "Keiyoushi",
    )

    // History and pins only exist in the site's remoteStorage cache, reachable through its own JS
    private suspend fun fetchHistory(): List<HistoryEntryDto> = runWebView<String>(10.seconds) {
        userAgent = headers["User-Agent"]!!
        jsBridge("android") { resolve(it) }
        onPageFinished {
            evaluateJs(
                "Promise.all([globalHistoryHandler.getAllPinnedSeries(), globalHistoryHandler.getAllUnpinnedSeries()])" +
                    ".then(e => android.post(JSON.stringify(e.flat())))",
            )
        }
        loadUrl("$baseUrl/")
    }.parseAs()

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(fetchHistory(), SortType.UNPINNED)

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(fetchHistory(), SortType.PINNED)

    private fun seriesApiUrl(url: String): String {
        val urlComponents = url.split("/")
        val source = urlComponents[2]
        val slug = urlComponents[3]

        return "$baseUrl/read/api/$source/series/$slug/"
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val series = client.get(seriesApiUrl(manga.url)).parseAs<SeriesDto>()

        return SMangaUpdate(series.toSManga(manga.url), parseChapterList(series, manga))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = when {
        chapter.url.contains("/chapter/") -> {
            client.get("$baseUrl${chapter.url}")
                .parseAs<JsonArray>()
                .mapIndexed { i, jsonEl -> Page(i, "", jsonEl.pageSrc()) }
        }

        else -> {
            val series = client.get(seriesApiUrl(chapter.url)).parseAs<SeriesDto>()
            seriesJsonPageListParse(series, chapter)
        }
    }

    private fun seriesJsonPageListParse(series: SeriesDto, chapter: SChapter): List<Page> {
        val groupMap = series.groups.entries.associateBy({ it.value.ifEmpty { "default" } }, { it.key })
        val chapterScanlator = chapter.scanlator ?: "default" // workaround for "" as group causing NullPointerException (#13772)

        // prevent NullPointerException when chapters.key is 084 and chapter.chapter_number is 84
        val chapters = series.chapters.mapKeys {
            it.key.replace(Regex("^0+(?!$)"), "")
        }

        val chapterDto = chapters[chapter.chapter_number.toString()]
            ?: chapters[chapter.chapter_number.toInt().toString()]!!

        val pages = chapterDto.groups[groupMap[chapterScanlator]]!! as JsonArray

        return pages.mapIndexed { i, jsonEl ->
            Page(i, "", jsonEl.pageSrc())
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val (source, slug) = parseUrl(url) ?: return null
        return fetchSeries(source, slug)
    }

    private suspend fun fetchSeries(source: String, slug: String): SManga {
        val series = client.get("$baseUrl/read/api/$source/series/$slug/").parseAs<SeriesDto>()
        // Only tag for recently read on search; a failed tag shouldn't fail the search
        runCatching { tagHistory(source, slug) }

        return series.toSManga("/read/$source/$slug")
    }

    // The series page adds itself to the site's history. tag() is re-run so that
    // history-ready fires after our listener is attached.
    private suspend fun tagHistory(source: String, slug: String) = runWebView<Unit>(10.seconds) {
        userAgent = headers["User-Agent"]!!
        jsBridge("android") { resolve(Unit) }
        onPageFinished {
            evaluateJs("window.addEventListener('history-ready', () => android.post(''), { once: true }); tag();")
        }
        loadUrl("$baseUrl/read/$source/$slug/")
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        // legacy cubari:source/slug format
        if (query.startsWith("cubari:")) {
            val queryFragments = query.substringAfter("cubari:").split("/", limit = 2)
            return MangasPage(listOf(fetchSeries(queryFragments[0], queryFragments[1])), false)
        }

        val filtered = fetchHistory().filter { it.matches(query.trim()) }
        val mangasPage = parseMangaList(filtered, SortType.ALL)
        require(mangasPage.mangas.isNotEmpty()) { SEARCH_FALLBACK_MSG }

        return mangasPage
    }

    private fun parseUrl(url: HttpUrl): Pair<String, String>? {
        val host = url.host
        val pathSegments = url.pathSegments

        return if (
            host.endsWith("imgur.com") &&
            pathSegments.size >= 2 &&
            pathSegments[0] in listOf("a", "gallery")
        ) {
            "imgur" to pathSegments[1]
        } else if (
            host.endsWith("reddit.com") &&
            pathSegments.size >= 2 &&
            pathSegments[0] == "gallery"
        ) {
            "reddit" to pathSegments[1]
        } else if (
            host == "imgchest.com" &&
            pathSegments.size >= 2 &&
            pathSegments[0] == "p"
        ) {
            "imgchest" to pathSegments[1]
        } else if (
            host.endsWith("catbox.moe") &&
            pathSegments.size >= 2 &&
            pathSegments[0] == "c"
        ) {
            "catbox" to pathSegments[1]
        } else if (
            host.endsWith("cubari.moe") &&
            pathSegments.size >= 3
        ) {
            pathSegments[1] to pathSegments[2]
        } else if (
            host.endsWith(".githubusercontent.com")
        ) {
            val src = host.substringBefore(".")
            val path = url.encodedPath

            "gist" to Base64.encodeToString("$src$path".toByteArray(), Base64.NO_PADDING)
        } else {
            null
        }
    }

    // ------------- Helpers and whatnot ---------------

    private val volumeNotSpecifiedTerms = setOf("Uncategorized", "null", "")

    private fun parseChapterList(series: SeriesDto, manga: SManga): List<SChapter> {
        val chapterList = series.chapters.entries.flatMap { chapterEntry ->
            val chapterNum = chapterEntry.key
            val chapterObj = chapterEntry.value
            val volume = chapterObj.volume.content.let {
                if (volumeNotSpecifiedTerms.contains(it)) null else it
            }
            val title = chapterObj.title.orEmpty()

            chapterObj.groups.entries.map { groupEntry ->
                val groupNum = groupEntry.key
                val releaseDate = chapterObj.releaseDate?.get(groupNum)

                SChapter.create().apply {
                    scanlator = series.groups[groupNum]!!
                    chapter_number = chapterNum.toFloatOrNull() ?: -1f

                    date_upload = if (releaseDate != null) {
                        releaseDate.double.toLong() * 1000
                    } else {
                        0L
                    }

                    name = buildString {
                        if (!volume.isNullOrBlank()) append("Vol.$volume ")
                        append("Ch.$chapterNum")
                        if (title.isNotBlank()) append(" - $title")
                    }

                    url = if (groupEntry.value is JsonArray) {
                        "${manga.url}/$chapterNum/$groupNum"
                    } else {
                        groupEntry.value.pageSrc()
                    }
                }
            }
        }

        return chapterList.sortedByDescending { it.chapter_number }
    }

    private fun parseMangaList(payload: List<HistoryEntryDto>, sortType: SortType): MangasPage {
        val mangaList = payload.mapNotNull { entry ->
            if (sortType == SortType.PINNED && entry.pinned) {
                entry.toSManga()
            } else if (sortType == SortType.UNPINNED && !entry.pinned) {
                entry.toSManga()
            } else if (sortType == SortType.ALL) {
                entry.toSManga()
            } else {
                null
            }
        }

        return MangasPage(mangaList, false)
    }

    companion object {
        const val AUTHOR_FALLBACK = "Unknown"
        const val ARTIST_FALLBACK = "Unknown"
        const val DESCRIPTION_FALLBACK = "No description."
        const val SEARCH_FALLBACK_MSG = "Please enter a valid Cubari URL"

        enum class SortType {
            PINNED,
            UNPINNED,
            ALL,
        }
    }
}
