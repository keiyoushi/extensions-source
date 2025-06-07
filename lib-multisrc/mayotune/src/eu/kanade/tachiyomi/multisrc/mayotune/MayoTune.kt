package eu.kanade.tachiyomi.multisrc.mayotune

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.getValue

abstract class MayoTune(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : ParsedHttpSource() {
    private val json: Json by injectLazy()
    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    open val sourceList = listOf(SManga.create())

    // Popular
    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return Observable.just(MangasPage(sourceList, false))
    }

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularMangaNextPageSelector(): String? = throw UnsupportedOperationException()
    override fun popularMangaSelector(): String = throw UnsupportedOperationException()
    override fun popularMangaFromElement(element: Element) = throw UnsupportedOperationException()

    // Latest
    override val supportsLatest: Boolean = true
    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return Observable.just(MangasPage(sourceList, false))
    }

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesNextPageSelector(): String? = throw UnsupportedOperationException()
    override fun latestUpdatesSelector(): String = throw UnsupportedOperationException()
    override fun latestUpdatesFromElement(element: Element): SManga =
        throw UnsupportedOperationException()

    // Search
    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        val mangas = mutableListOf<SManga>()
        sourceList.map {
            if (it.title.lowercase().contains(query.lowercase())) {
                mangas.add(it)
            }
        }
        return Observable.just(MangasPage(mangas, false))
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        throw UnsupportedOperationException()

    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException()
    override fun searchMangaSelector() = throw UnsupportedOperationException()
    override fun searchMangaFromElement(element: Element) = throw UnsupportedOperationException()

    // Get Override
    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(manga.url, headers)
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET(manga.url + "/api/chapters", headers)
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(chapter.url, headers)
    }

    // Details

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val statusText =
            document.select("div.text-center:contains(Status)").text().substringBefore("Status")
                .trim()

        url = sourceList.first().url
        title = sourceList.first().title
        artist = sourceList.first().artist
        author = sourceList.first().author
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
            ?.ifEmpty { sourceList.first().thumbnail_url }
    }
    // Chapters

    override fun chapterListSelector() = throw UnsupportedOperationException()
    override fun chapterFromElement(element: Element): SChapter =
        throw UnsupportedOperationException()

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = json.decodeFromString<List<ChapterDto>>(response.body.string())
        return chapters.sortedByDescending { it.number }.map { chapter ->
            SChapter.create().apply {
                url = chapter.getChapterURL(baseUrl)
                name = chapter.getChapterTitle()
                chapter_number = chapter.number
                date_upload = chapter.date.let { sdf.parse(it)?.time } ?: 0L
            }
        }
    }
    // Pages

    override fun pageListParse(document: Document): List<Page> =
        document.select("div.w-full > img").mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("src"))
        }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()
}
