package eu.kanade.tachiyomi.extension.fr.twatt

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.lib.dataimage.DataImageInterceptor
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Twatt : HttpSource() {

    override val name = "Twatt"

    override val baseUrl = "https://twatt.fr"

    override val lang = "fr"

    override val supportsLatest = false

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(DataImageInterceptor())
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/projects", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val projects = response.parseAs<ProjectsResponse>().projects
        val mangas = projects.map { it.toSManga(baseUrl) }
        return MangasPage(mangas, false)
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // =============================== Search ===============================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = fetchPopularManga(page).map { mangasPage ->
        val filtered = mangasPage.mangas.filter { it.title.contains(query, ignoreCase = true) }
        MangasPage(filtered, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // =========================== Manga Details ============================

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/api/series/${manga.url.substringAfterLast('/')}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.parseAs<SeriesResponse>()
        return data.project.toSManga(baseUrl).apply {
            data.mainTeam?.let { author = it.name }
        }
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl/api/series/${manga.url.substringAfterLast('/')}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<SeriesResponse>()
        return data.chapters.map { entry ->
            SChapter.create().apply {
                url = "/chapitre/${entry.id}"
                name = entry.title?.ifBlank { null }
                    ?: "Chapitre ${entry.number}"
                chapter_number = entry.number.toFloat()
                date_upload = DATE_FORMAT.tryParse(entry.releasedAt)
            }
        }
    }

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl/api/chapters/${chapter.url.substringAfterLast('/')}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val images = response.parseAs<ChapterResponse>().chapter.images
        return images.mapIndexed { i, path ->
            Page(i, imageUrl = resolvePath(path, baseUrl))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
