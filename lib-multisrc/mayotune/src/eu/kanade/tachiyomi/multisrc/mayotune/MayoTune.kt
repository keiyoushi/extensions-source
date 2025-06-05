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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.getValue

abstract class MayoTune(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : ParsedHttpSource() {
    open val sourceList = listOf(SManga.create())

    private val json: Json by injectLazy()

    // Info

    override val supportsLatest: Boolean = false

    // Popular
    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return Observable.just(MangasPage(sourceList, false))
    }

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularMangaNextPageSelector(): String? = throw UnsupportedOperationException()
    override fun popularMangaSelector(): String = throw UnsupportedOperationException()
    override fun popularMangaFromElement(element: Element) = throw UnsupportedOperationException()

    // Latest
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
        return GET(manga.url + "api/chapters", headers)
    }
    override fun pageListRequest(chapter: SChapter): Request {
        return GET(chapter.url, headers)
    }

    // Details

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        url = sourceList.first().url
        title = sourceList.first().title
        artist = sourceList.first().artist
        author = sourceList.first().author
        description = document.select(".text-lg").text()
        genre = document.select("span.text-sm:nth-child(2)").text().replace("•", ",")
        status = when (document.select(".text-green-400").text()) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            "Cancelled" -> SManga.CANCELLED
            "Hiatus" -> SManga.ON_HIATUS
            "Finished" -> SManga.PUBLISHING_FINISHED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = baseUrl + document.select(".object-contain").attr("src")
    }
    // Chapters

    override fun chapterListSelector() = throw UnsupportedOperationException()
    override fun chapterFromElement(element: Element): SChapter =
        throw UnsupportedOperationException()

    override fun chapterListParse(response: Response): List<SChapter> {
        val jsonObj = json.decodeFromString<JsonArray>(response.body.string())
        return jsonObj.sortedByDescending {
            it.jsonObject["number"]?.jsonPrimitive?.float
        }.map { json ->
            SChapter.create().apply {
                url = "${baseUrl}chapter/${json.jsonObject["id"]?.jsonPrimitive?.contentOrNull}"
                name =
                    if (!json.jsonObject["title"]?.jsonPrimitive?.contentOrNull.isNullOrEmpty()) {
                        "Chapter ${json.jsonObject["number"]?.jsonPrimitive?.contentOrNull}: " + json.jsonObject["title"]?.jsonPrimitive?.contentOrNull
                    } else {
                        "Chapter ${json.jsonObject["number"]?.jsonPrimitive?.contentOrNull}"
                    }
                chapter_number = json.jsonObject["number"]?.jsonPrimitive?.float ?: 0f
                date_upload = json.jsonObject["date"]?.jsonPrimitive?.contentOrNull?.let {
                    var sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    sdf.timeZone = TimeZone.getDefault()
                    val date = sdf.parse(it)
                    date?.time
                } ?: 0L
            }
        }
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> =
        document.select("div.w-full > img").mapIndexed { index, img ->
            Page(
                index,
                "",
                baseUrl + img.attr("src"),
            )
        }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()
}
