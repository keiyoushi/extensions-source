package eu.kanade.tachiyomi.extension.ja.comicborder

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.Request
import okhttp3.Response

class ComicBorder :
    GigaViewer(
        "Comic Border",
        "https://comicborder.com",
        "ja",
        "https://cdn-img.comicborder.com/public",
        true,
    ) {
    override val supportsLatest = false

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::imageIntercept)
        .build()

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("section.top-series").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.selectFirst(".top-series-nav a")!!.absUrl("href"))
                title = it.selectFirst("h3")!!.text()
                thumbnail_url = it.selectFirst(".top-key-image")?.absUrl("data-src")
            }
        }
        return MangasPage(mangas, false)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val referer = response.request.url.toString()
        val aggregateId = document.selectFirst("script.js-valve")!!.attr("data-giga_series")
        val chapters = mutableListOf<SChapter>()

        var offset = 0

        while (true) {
            val result = paginatedChaptersRequest(referer, aggregateId, offset)
            val resultData = result.parseAs<List<Dto>>()

            if (resultData.isEmpty()) break

            resultData.mapTo(chapters) {
                it.toSChapter(publisher)
            }
            offset += resultData.size
        }
        return chapters
    }

    override fun getFilterList(): FilterList = FilterList()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
