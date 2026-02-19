package eu.kanade.tachiyomi.extension.all.mayotune

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class MayoTune(
    override val lang: String,
    private val chapterEndpoint: String,
) : HttpSource() {
    override val name: String = "MayoTune"
    override val baseUrl: String = "https://mayochuu.xyz"
    override val versionId: Int = 1

    private val names = mapOf(
        "en" to "Tune In to the Midnight Heart",
        "ja" to "真夜中ハートチューン",
        "all" to "Mayonaka Heart Tune",
    )

    private val source = SManga.create().apply {
        title = names[lang] ?: names["all"]!!
        url = "/"
        thumbnail_url = "$baseUrl/img/cover.jpg"
        author = "Masakuni Igarashi"
    }

    // Popular
    override fun fetchPopularManga(page: Int): Observable<MangasPage> = Observable.just(MangasPage(listOf(source), false))

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // Latest
    override val supportsLatest: Boolean = true
    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> = Observable.just(MangasPage(listOf(source), false))

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // Search
    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        val mangas = mutableListOf<SManga>()

        if (names.any { it.value.lowercase().contains(query.lowercase()) } ||
            source.author?.lowercase()?.contains(query.lowercase()) == true
        ) {
            mangas.add(source)
        }

        return Observable.just(MangasPage(mangas, false))
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // Get Override
    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl/api/$chapterEndpoint/chapters", headers)

    override fun getChapterUrl(chapter: SChapter): String {
        val id = (baseUrl + chapter.url).toHttpUrl().queryParameter("id")
        return "$baseUrl/$chapterEndpoint/chapter/$id"
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
                url = chapter.getChapterURL(chapterEndpoint)
                name = chapter.getChapterTitle()
                chapter_number = chapter.number
                date_upload = chapter.getDateTimestamp()
            }
        }
    }

    // Pages
    override fun pageListParse(response: Response): List<Page> {
        val chapter = response.parseAs<ChapterDto>()
        return List(chapter.pageCount) { index ->
            Page(index, imageUrl = "$baseUrl/api/manga/${chapter.id}/${index + 1}")
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
