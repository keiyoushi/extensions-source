package eu.kanade.tachiyomi.extension.tr.epikmanga

import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable

class EpikManga : FMReader("Epik Manga", "https://www.epikmanga.com", "tr") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/seri-listesi?sorting=views&sorting-type=DESC&Sayfa=$page", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/seri-listesi?sorting=lastUpdate&sorting-type=DESC&Sayfa=$page", headers)
    override fun popularMangaNextPageSelector() = "ul.pagination li.active + li:not(.disabled)"

    override val headerSelector = "h4 a"

    // search wasn't working on source's website
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                searchMangaParse(response, query)
            }
    }
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/seri-listesi?type=text", headers)
    private fun searchMangaParse(response: Response, query: String): MangasPage {
        val mangas = response.asJsoup().select("div.char.col-lg-4 a").toList()
            .filter { it.text().contains(query, ignoreCase = true) }
            .map {
                SManga.create().apply {
                    setUrlWithoutDomain(it.attr("href"))
                    title = it.text()
                }
            }
        return MangasPage(mangas, false)
    }
    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.col-md-9 div.row").first()!!

        return SManga.create().apply {
            status = parseStatus(infoElement.select("h4:contains(Durum:)").firstOrNull()?.ownText())
            author = infoElement.select("h4:contains(Yazar:)").firstOrNull()?.ownText()
            artist = infoElement.select("h4:contains(Çizer:)").firstOrNull()?.ownText()
            genre = infoElement.select("h4:contains(Türler:) a").joinToString { it.text() }
            thumbnail_url = infoElement.select("img.thumbnail").imgAttr()
            description = document.select("div.col-md-12 p").text()
        }
    }
    override fun chapterListSelector() = "table.table tbody tr"
    override fun getFilterList(): FilterList = FilterList()
}
