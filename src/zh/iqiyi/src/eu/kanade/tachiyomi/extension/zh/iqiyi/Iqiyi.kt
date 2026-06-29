package eu.kanade.tachiyomi.extension.zh.iqiyi

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.Request
import okhttp3.Response

class Iqiyi : HttpSource() {
    override val name: String = "爱奇艺叭嗒"
    override val lang get() = "zh-Hans"
    override val id get() = 2198877009406729694
    override val supportsLatest: Boolean = true
    override val baseUrl: String = "https://www.iqiyi.com/manhua"

    override fun headersBuilder() = super.headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36")

    // Popular

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/category/全部_-1_-1_9_$page/", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("ul.cartoon-hot-ul > li.cartoon-hot-list").map { element ->
            SManga.create().apply {
                title = element.selectFirst("a.cartoon-item-tit")!!.text()
                url = element.selectFirst("a.cartoon-item-tit")!!.absUrl("href").removePrefix(baseUrl)
                thumbnail_url = element.selectFirst("img")!!.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("div.mod-page > a.a1:contains(下一页)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/category/全部_-1_-1_4_$page/", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("ul.cartoon-hot-ul > li.cartoon-hot-list").map { element ->
            SManga.create().apply {
                title = element.selectFirst("a.cartoon-item-tit")!!.text()
                url = element.selectFirst("a.cartoon-item-tit")!!.absUrl("href").removePrefix(baseUrl)
                thumbnail_url = element.selectFirst("img")!!.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("div.mod-page > a.a1:contains(下一页)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/search-keyword=${query}_$page", headers)

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("ul.stacksList > li.stacksBook").map { element ->
            SManga.create().apply {
                title = element.selectFirst("h3.stacksBook-tit > a")!!.text()
                url = element.selectFirst("h3.stacksBook-tit > a")!!.absUrl("href").removePrefix(baseUrl)
                thumbnail_url = element.selectFirst("img")!!.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("div.mod-page > a.a1:contains(下一页)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Details

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("div.detail-tit > h1")!!.text()
            thumbnail_url = document.selectFirst("div.detail-cover > img")!!.absUrl("src")
            author = document.selectFirst("p.author > span.author-name")!!.text()
            artist = author
            genre = document.select("div.detail-tit > a.detail-categ").eachText().joinToString(", ")
            description = document.selectFirst("p.detail-docu")!!.text()
            status = when (document.selectFirst("span.cata-info")!!.text()) {
                "连载中" -> SManga.ONGOING
                "完结" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // Chapters

    override fun chapterListRequest(manga: SManga): Request {
        val id = manga.url.substringAfter("detail_").substringBefore(".html")
        return GET("$baseUrl/catalog/$id/", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> = response.parseAs<Dto>().toChapterList()

    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        if (document.select("div.main > p.pay-title").isNotEmpty()) {
            throw Exception("本章为付费章节")
        }
        return document.select("ul.main-container > li.main-item > img").mapIndexed { index, element ->
            if (element.hasAttr("data-original")) {
                Page(index, imageUrl = element.absUrl("data-original"))
            } else {
                Page(index, imageUrl = element.absUrl("src"))
            }
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
