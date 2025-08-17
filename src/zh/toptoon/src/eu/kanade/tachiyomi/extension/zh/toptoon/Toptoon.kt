package eu.kanade.tachiyomi.extension.zh.toptoon

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
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class Toptoon : HttpSource() {
    override val name: String = "TOPTOON頂通"
    override val lang: String = "zh"
    override val supportsLatest = true
    override val baseUrl = "https://www.toptoon.net"

    // Popular

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/ranking", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val jsonUrl = response.body.string()
            .substringAfter("jsonFileUrl: [\"")
            .substringBefore("\"")
            .replace("\\/", "/")
        val jsonResponse = client.newCall(GET("https:$jsonUrl", headers)).execute()
        val mangas = jsonResponse.parseAs<PopularResponseDto>().adult.map {
            it.toSManga()
        }
        return MangasPage(mangas, false)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/search", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val jsonUrl = response.body.string()
            .substringAfter("var jsonFileUrl = '")
            .substringBefore("'")
        val jsonResponse = client.newCall(GET("https:$jsonUrl", headers)).execute()
        val mangas = jsonResponse.parseAs<Map<String, MangaDto>>().values
            .sortedByDescending { it.lastUpdated.pubDate }
            .map {
                it.toSManga()
            }
        return MangasPage(mangas, false)
    }

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/search#$query", headers)

    override fun searchMangaParse(response: Response): MangasPage {
        val query = response.request.url.fragment!!
        val jsonUrl = response.body.string()
            .substringAfter("var jsonFileUrl = '")
            .substringBefore("'")
        val jsonResponse = client.newCall(GET("https:$jsonUrl", headers)).execute()
        val mangas = jsonResponse.parseAs<Map<String, MangaDto>>().values
            .map {
                it.toSManga()
            }
            .filter { it.title.contains(query, true) || it.author!!.contains(query, true) }
        return MangasPage(mangas, false)
    }

    // Details

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
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

    override fun chapterListParse(response: Response): List<SChapter> {
        if (response.request.url.pathSegments[0].isEmpty()) {
            throw Exception("请到WebView确认年满18岁")
        }
        val document = response.asJsoup()
        return document.select("section.episode_area ul.list_area li.episodeBox").map {
            SChapter.create().apply {
                setUrlWithoutDomain(it.selectFirst("a")!!.absUrl("href"))
                name = if (it.selectFirst("button.coin, button.gift, button.waitFree") != null) {
                    "\uD83D\uDD12" // lock emoji
                } else {
                    ""
                } + it.selectFirst("div.title")!!.text() + " " +
                    it.selectFirst("div.subTitle")!!.text()
                date_upload = dateFormat.tryParse(it.selectFirst("div.pubDate")?.text())
            }
        }.asReversed()
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val pathSegments = response.request.url.pathSegments
        if (pathSegments[0].isEmpty()) {
            throw Exception("请到WebView确认年满18岁")
        } else if (pathSegments.size < 2 || pathSegments[1] != "epView") {
            throw Exception("请确认是否已登录解锁")
        }
        val document = response.asJsoup()
        val images = document.select("article.epContent section.imgWrap div.cImg img")
        return images.mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("data-src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    }
}
