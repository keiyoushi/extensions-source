package eu.kanade.tachiyomi.extension.ar.mangatime

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.io.IOException
import java.lang.UnsupportedOperationException
import java.text.SimpleDateFormat
import java.util.Locale

class MangaTime : HttpSource() {
    override val baseUrl = "https://mangatime.org"

    override val name = "MangaTime"

    override val lang = "ar"

    override val supportsLatest = false

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)

    private val perPage: Int = 24

    private fun getPageRange(page: Int): String {
        val start = (page - 1) * perPage
        val end = page * perPage
        return "$start/$end"
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-setbg-high") -> attr("abs:data-setbg-high")
        hasAttr("data-setbg-low") -> attr("abs:data-setbg-low")
        hasAttr("data-setbg") -> attr("abs:data-setbg-low")
        hasAttr("data-src") -> attr("abs:data-src")
        else -> attr("abs:src")
    }

    private fun replaceNameWithDash(inputString: String): String {
        var replacedString = inputString.replace("\\s+".toRegex(), "-")
        return replacedString.replace("[^a-zA-Z0-9-]".toRegex(), "")
    }

    private fun String?.toStatus() = when (this) {
        "مستمرة" -> SManga.ONGOING
        "قادم قريبًا" -> SManga.ONGOING
        "مكتمل" -> SManga.COMPLETED
        "متوقف" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    private fun addView(seriesId: String) {
        val body = """{"id":"$seriesId"}""".toRequestBody("application/json".toMediaType())
        val request = POST("$baseUrl/add_view", headers, body)

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: okio.IOException) {
                Log.e(name, "Failed to count views", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) Log.e(name, "Error: ${it.code}")
                }
            }
        })
    }

    // Popular

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/releases/${getPageRange(page)}/Newest/")

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val entries = document.select("a.manga-link").map { el ->
            SManga.create().apply {
                title = el.attr("manganame")
                url = "/manga/${replaceNameWithDash(title)}/"
                thumbnail_url = el.selectFirst("div[itemprop]")?.imgAttr()
            }
        }

        val hasNextPage = document.selectFirst(".current-page + #next-page") == null

        return MangasPage(entries, hasNextPage)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .build()

        return GET(url)
    }

    override fun searchMangaParse(response: Response) = MangasPage(popularMangaParse(response).mangas, false)

    // Details

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup().select("div.anime__details__text")
        title = document.select("h3").text()
        description = document.select("p").text()
        thumbnail_url = document.selectFirst("div.set-bg")?.imgAttr()
        status = document.selectFirst("li:contains(الحالة)")?.ownText().toStatus()
        genre = document.selectFirst("li:contains(التصنيفات)")?.ownText()?.replace("،", ",")
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val src = response.asJsoup().selectFirst("script:containsData(chapters = [)")!!.data()
        val chapters = src.substringAfter("chapters = ")
            .substringBefore(";")
            .parseAs<List<ChapterDto>>()

        val seriesId = src.substringAfter("id: \"").substringBefore("\"")

        addView(seriesId)

        return chapters.map { chapter ->
            SChapter.create().apply {
                chapter_number = chapter.order.toFloat()
                name = chapter.name
                setUrlWithoutDomain("${response.request.url}chapter-${chapter.order}/#$seriesId#${chapter.pages.toJsonString()}")
                date_upload = dateFormat.tryParse(chapter.uploadTime)
            }
        }
    }

    // Pages

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val seriesId = chapter.url.substringAfter("#").substringBefore("#")

        addView(seriesId)

        val pagesId = chapter.url.substringAfterLast("#").parseAs<List<String>>()
        return Observable.just(
            pagesId.mapIndexed { index, id ->
                Page(index, imageUrl = "$baseUrl/get_image/$id")
            },
        )
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    @Serializable
    class ChapterDto(
        val name: String,
        val order: Int,
        @SerialName("upload_time") val uploadTime: String,
        val pages: List<String>,
    )
}
