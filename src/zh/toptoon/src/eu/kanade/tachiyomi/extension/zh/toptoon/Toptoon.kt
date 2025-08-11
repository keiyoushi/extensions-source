package eu.kanade.tachiyomi.extension.zh.toptoon

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class Toptoon : ParsedHttpSource() {
    override val name: String = "Toptoon頂通"
    override val lang: String = "zh"
    override val supportsLatest = true
    override val baseUrl = "https://www.toptoon.net"
    private val json: Json by injectLazy()

    // Popular

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/ranking", headers)
    override fun popularMangaNextPageSelector() = throw UnsupportedOperationException()
    override fun popularMangaSelector() = throw UnsupportedOperationException()
    override fun popularMangaFromElement(element: Element) = throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response): MangasPage {
        val jsonUrl = response.body.string()
            .substringAfter("jsonFileUrl: [\"")
            .substringBefore("\"")
            .replace("\\/", "/")
        val jsonResponse = client.newCall(GET("https:$jsonUrl", headers)).execute()
        val mangas = jsonResponse.parseAs<PopularResponseDto>().adult.map {
            SManga.create().apply {
                title = it.meta.title
                url = it.url
                thumbnail_url = it.thumbnail.url
            }
        }
        return MangasPage(mangas, false)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/search", headers)
    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()
    override fun latestUpdatesSelector() = throw UnsupportedOperationException()
    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage {
        val jsonUrl = response.body.string()
            .substringAfter("var jsonFileUrl = '")
            .substringBefore("'")
        val jsonResponse = client.newCall(GET("https:$jsonUrl", headers)).execute()
        val mangas = jsonResponse.parseAs<Map<String, MangaDto>>().values
            .sortedByDescending { it.lastUpdated.pubDate }
            .map {
                SManga.create().apply {
                    title = it.meta.title
                    url = it.url
                    thumbnail_url = it.thumbnail.url
                }
            }
        return MangasPage(mangas, false)
    }

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/search", headers)
    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException()
    override fun searchMangaSelector() = throw UnsupportedOperationException()
    override fun searchMangaFromElement(element: Element) = throw UnsupportedOperationException()

    // Copied from Mihon, I only change parameter of searchMangaParse
    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return Observable.defer {
            try {
                client.newCall(searchMangaRequest(page, query, filters)).asObservableSuccess()
            } catch (e: NoClassDefFoundError) {
                // RxJava doesn't handle Errors, which tends to happen during global searches
                // if an old extension using non-existent classes is still around
                throw RuntimeException(e)
            }
        }
            .map { response ->
                searchMangaParse(response, query)
            }
    }

    private fun searchMangaParse(response: Response, query: String): MangasPage {
        val jsonUrl = response.body.string()
            .substringAfter("var jsonFileUrl = '")
            .substringBefore("'")
        val jsonResponse = client.newCall(GET("https:$jsonUrl", headers)).execute()
        val mangas = jsonResponse.parseAs<Map<String, MangaDto>>().values
            .filter { it.meta.title.contains(query, true) || it.meta.author.authorString.contains(query, true) }
            .map {
                SManga.create().apply {
                    title = it.meta.title
                    url = it.url
                    thumbnail_url = it.thumbnail.url
                }
            }
        return MangasPage(mangas, false)
    }

    // Details

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("section.infoContent div.title")!!.text()
        thumbnail_url = document.selectFirst("div.comicThumb img")!!.absUrl("src")
        author = document.selectFirst("section.infoContent div.etc")!!.text()
            .substringAfter("作家 : ").substringBefore("|")
        description = document.selectFirst("div.comic_story div.desc")!!.text()
        genre = document.selectFirst("section.infoContent div.hashTag")?.text()
            ?.replace("#", ", ")
        if (document.selectFirst("div.etc span.comicDayBox") != null) {
            status = SManga.ONGOING
        } else if (document.selectFirst("div.hashTag a[href=/search/keyword/79]") != null) {
            status = SManga.COMPLETED
        }
    }

    // Chapters

    override fun chapterListSelector(): String = "section.episode_area ul.list_area li.episodeBox"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        name = if (element.selectFirst("button.coin, button.gift, button.waitFree") != null) {
            "\uD83D\uDD12"
        } else {
            ""
        } + element.selectFirst("div.title")!!.text() + " " +
            element.selectFirst("div.subTitle")!!.text()
        date_upload = parseDate(element.selectFirst("div.pubDate")?.text() ?: "")
    }
    override fun chapterListParse(response: Response): List<SChapter> {
        if (response.request.url.pathSegments[0].isEmpty()) {
            throw Exception("请到WebView确认年满18岁")
        }
        return super.chapterListParse(response).asReversed()
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val pathSegments = response.request.url.pathSegments
        if (pathSegments[0].isEmpty()) {
            throw Exception("请到WebView确认年满18岁")
        } else if (pathSegments.size < 2 || pathSegments[1] != "epView") {
            throw Exception("请确认是否已登录解锁")
        }
        return super.pageListParse(response)
    }

    override fun pageListParse(document: Document): List<Page> {
        val images = document.select("article.epContent section.imgWrap div.cImg img")
        return images.mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("data-src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromStream(it.body.byteStream())
    }

    private fun parseDate(dateStr: String): Long {
        return try {
            dateFormat.parse(dateStr)!!.time
        } catch (_: ParseException) {
            0L
        }
    }

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    }
}
