package eu.kanade.tachiyomi.extension.fr.fmteam

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class FMTEAM : HttpSource() {
    override val name = "FMTEAM"
    override val baseUrl = "https://fmteam.fr"
    override val lang = "fr"
    override val supportsLatest = true
    override val versionId = 2

    private val json: Json by injectLazy()

    companion object {
        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }
    }

    // All manga
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/comics")

    override fun popularMangaParse(response: Response): MangasPage {
        val results = json.decodeFromString<FmteamComicListPage>(response.body.string())

        return MangasPage(results.comics.sortedByDescending { it.views }.map(::fmTeamComicToSManga), false)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/api/comics")

    override fun latestUpdatesParse(response: Response): MangasPage {
        val results = json.decodeFromString<FmteamComicListPage>(response.body.string())

        return MangasPage(results.comics.sortedByDescending { parseDate(it.last_chapter.published_on) }.map(::fmTeamComicToSManga), false)
    }

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/api/search/$query")

    override fun searchMangaParse(response: Response): MangasPage {
        val results = json.decodeFromString<FmteamComicListPage>(response.body.string())

        return MangasPage(results.comics.map(::fmTeamComicToSManga), false)
    }

    // Manga details
    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/api${manga.url}")

    override fun mangaDetailsParse(response: Response): SManga {
        val results = json.decodeFromString<FmteamComicDetailPage>(response.body.string())

        return fmTeamComicToSManga(results.comic)
    }

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl${manga.url}"
    }

    // Chapter list
    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl/api${manga.url}")

    override fun chapterListParse(response: Response): List<SChapter> {
        val results = json.decodeFromString<FmteamComicDetailPage>(response.body.string())

        return results.comic.chapters?.map(::fmTeamChapterToSChapter) ?: emptyList()
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return "$baseUrl${chapter.url}"
    }

    // Pages
    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl/api${chapter.url}")

    override fun pageListParse(response: Response): List<Page> {
        val results = json.decodeFromString<FmteamChapterDetailPage>(response.body.string())

        return results.chapter.pages.orEmpty()
            .mapIndexed { i, page -> Page(i, "${results.chapter.url}#${i + 1}", page) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used.")

    // Utils
    private fun fmTeamComicToSManga(comic: FmteamComic): SManga = SManga.create().apply {
        url = comic.url
        title = comic.title
        artist = comic.artist
        author = comic.author
        description = comic.description
        genre = comic.genres.joinToString { it.name }
        status = when (comic.status) {
            "En cours" -> SManga.ONGOING
            "Termin\u00e9" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = comic.thumbnail
        initialized = true
    }

    private fun fmTeamChapterToSChapter(chapter: FmteamChapter): SChapter = SChapter.create().apply {
        url = chapter.url
        name = chapter.full_title
        date_upload = parseDate(chapter.published_on)
        scanlator = chapter.teams.filterNotNull().joinToString { it.name }
    }

    private fun parseDate(dateStr: String): Long {
        return runCatching { DATE_FORMATTER.parse(dateStr)?.time }
            .getOrNull() ?: 0L
    }
}
