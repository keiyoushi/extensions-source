package eu.kanade.tachiyomi.extension.ja.cmoa

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.cookieinterceptor.CookieInterceptor
import keiyoushi.lib.speedbinb.SpeedBinbInterceptor
import keiyoushi.lib.speedbinb.SpeedBinbReader
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale

class Cmoa : HttpSource() {
    override val name = "C'moA"
    override val baseUrl = "https://www.cmoa.jp"
    override val lang = "ja"
    override val supportsLatest = true

    private val json = Injekt.get<Json>()
    private val reader by lazy { SpeedBinbReader(client, headers, json, true) }
    private val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.JAPAN)

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(SpeedBinbInterceptor(json))
        .addNetworkInterceptor(CookieInterceptor(baseUrl, "safesearch" to "0"))
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36")

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/search/purpose/ranking/all".toHttpUrl().newBuilder()
            .addQueryParameter("period", "daily")
            .addQueryParameter("daily", "all")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("ul#ranking_result_list li.search_result_box").map {
            SManga.create().apply {
                title = it.selectFirst("div.search_result_box_right_sec1 a.title")!!.text()
                thumbnail_url = it.selectFirst("div.search_result_box_left img")?.absUrl("src")
                val id = it.selectFirst("div.search_result_box_left a.title")!!.absUrl("href").toHttpUrl().pathSegments[1]
                setUrlWithoutDomain(id)
            }
        }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/newrelease/?page=$page", headers)
    }
    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("ul.newrelease_list li").map {
            SManga.create().apply {
                title = it.selectFirst("div.title_name")!!.text()
                thumbnail_url = it.selectFirst("a.volume_img img")?.absUrl("src")
                val id = it.selectFirst("a.volume_img")!!.absUrl("href").toHttpUrl().pathSegments[1]
                setUrlWithoutDomain(id)
            }
        }
        val hasNextPage = document.selectFirst("div.swiper-button-next:not(.swiper-button-disabled)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        TODO()
    }

    override fun searchMangaParse(response: Response): MangasPage {
        TODO()
    }

    override fun mangaDetailsParse(response: Response): SManga {
       TODO()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        TODO()
    }

    override fun pageListParse(response: Response): List<Page> = reader.pageListParse(response)

    // Unsupported
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
