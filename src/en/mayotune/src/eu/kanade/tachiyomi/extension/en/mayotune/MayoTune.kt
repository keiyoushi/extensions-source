package eu.kanade.tachiyomi.extension.en.mayotune

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class MayoTune() : HttpSource() {
    override val name: String = "MayoTune"
    override val baseUrl: String = "https://mayotune.xyz"
    override val lang: String = "en"
    override val versionId: Int = 1

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val source = SManga.create().apply {
        title = "Mayonaka Heart Tune"
        url = baseUrl
        thumbnail_url = "$baseUrl/img/cover.jpg"
        author = "Masakuni Igarashi"
    }

    // Popular
    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return Observable.just(MangasPage(listOf(source), false))
    }

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException()

    // Latest
    override val supportsLatest: Boolean = true
    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return Observable.just(MangasPage(listOf(source), false))
    }

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage =
        throw UnsupportedOperationException()

    // Search
    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        val mangas = mutableListOf<SManga>()

        if (source.title.lowercase().contains(query.lowercase()) ||
            source.author?.lowercase()?.contains(query.lowercase()) == true
        ) {
            mangas.add(source)
        }

        return Observable.just(MangasPage(mangas, false))
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException()

    // Get Override
    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(manga.url, headers)
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET(manga.url + "/api/chapters", headers)
    }

    // Details

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        val statusText =
            document.selectFirst("div.text-center:contains(Status)")?.text()
                ?.substringBefore("Status")
                ?.trim()

        url = source.url
        title = source.title
        artist = source.artist
        author = source.author
        description = document.selectFirst(".text-lg")?.text()
        genre = document.selectFirst("span.text-sm:nth-child(2)")?.text()?.replace("•", ",")
        status = when (statusText) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            "Cancelled" -> SManga.CANCELLED
            "Hiatus" -> SManga.ON_HIATUS
            "Finished" -> SManga.PUBLISHING_FINISHED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = document.selectFirst("img.object-contain")?.absUrl("src")
            ?.ifEmpty { source.thumbnail_url }
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = response.parseAs<List<ChapterDto>>()
        return chapters.sortedByDescending { it.number }.map { chapter ->
            SChapter.create().apply {
                url = chapter.getChapterURL()
                name = chapter.getChapterTitle()
                chapter_number = chapter.number
                date_upload = sdf.tryParse(chapter.date)
            }
        }
    }
    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.w-full > img").mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
