package eu.kanade.tachiyomi.extension.zh.bh3

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

class BH3 : HttpSource() {

    override val name = "《崩坏3》IP站"

    override val baseUrl = "https://comic.bh3.com"

    override val lang = "zh"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/book", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.asJsoup().select("a[href*=book]").map { element ->
            SManga.create().apply {
                url = "/book/${element.selectFirst("div.container")?.attr("id").orEmpty()}"
                title = element.selectFirst("div.container-title")?.text().orEmpty()
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
            }
        }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw Exception("No search")

    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()

    override fun chapterListRequest(manga: SManga) = GET(baseUrl + manga.url + "/get_chapter", headers)

    override fun chapterListParse(response: Response): List<SChapter> = response.parseAs<List<Dto>>().map { it.toSChapter() }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            thumbnail_url = document.selectFirst("img.cover")?.attr("abs:src")
            description = document.selectFirst("div.detail_info1")?.text()
            title = document.selectFirst("div.title")?.text().orEmpty()
        }
    }

    override fun pageListParse(response: Response): List<Page> = response.asJsoup().select("img.lazy.comic_img").mapIndexed { i, el ->
        Page(i, imageUrl = el.attr("data-original"))
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
