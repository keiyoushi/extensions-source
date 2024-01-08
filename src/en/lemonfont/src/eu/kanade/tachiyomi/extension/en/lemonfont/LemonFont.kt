package eu.kanade.tachiyomi.extension.en.lemonfont

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class LemonFont : ParsedHttpSource() {
    override val name = "LemonFont"

    override val baseUrl = "http://lemonfontcomics.com"

    override val lang = "en"

    override val supportsLatest = false

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/comics/", headers)

    override fun popularMangaSelector() = "div.comic-collection > a:not([href*=redbubble])"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.select("p").text().trim()
        thumbnail_url = element.select("img").attr("abs:src")

        if (!element.attr("href").contains("http")) {
            setUrlWithoutDomain(element.attr("abs:href"))
        } else {
            status = SManga.LICENSED
            setUrlWithoutDomain(element.attr("href"))
        }
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val seriesUrl: String = document.location()
        val homePage: Document = client.newCall(GET("$baseUrl/comics/", headers)).execute().asJsoup()
        val element: Element = homePage.select("div.comic-collection > a[abs:href=$seriesUrl]").first()!!

        thumbnail_url = element.select("img").attr("abs:src")
        status = getStatus(element)
        author = "LemonFont"
    }

    private fun getStatus(element: Element) = when {
        element.attr("href").contains("http") -> SManga.LICENSED
        element.select("p").first()!!.id() == "tag-ongoing" -> SManga.ONGOING
        else -> SManga.COMPLETED
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaInfo = response.asJsoup().select("div.container > div.content > script").toString()

        val ptotal: Int = Integer.parseInt(Regex("(?<=window\\.ptotal = )([0-9]*?)(?=;)").find(mangaInfo)?.value!!)
        val part: String = Regex("(?<=window\\.part=')(.*?)(?=';)").find(mangaInfo)?.value ?: ""
        val series: String = Regex("(?<=window\\.series=')(.*?)(?=';)").find(mangaInfo)?.value ?: ""

        val chapterList: MutableList<SChapter> = ArrayList()
        for (i in 1..ptotal) {
            chapterList.add(
                SChapter.create().apply {
                    setUrlWithoutDomain("$baseUrl/assets/comics/$series/$part/${i.toString().padStart(3, '0')}.png")
                    chapter_number = i.toFloat()
                    name = "Chapter $i"
                },
            )
        }
        return chapterList.reversed()
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return Observable.just(listOf(Page(0, "", baseUrl + chapter.url)))
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return Observable.just(MangasPage(emptyList(), false))
    }

    override fun pageListParse(document: Document): List<Page> = throw UnsupportedOperationException("Not Used")

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException("Not Used")

    override fun chapterListSelector() = throw UnsupportedOperationException("Not Used")

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException("Not Used")

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException("Not Used")

    override fun latestUpdatesSelector() = throw UnsupportedOperationException("Not Used")

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException("Not Used")

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException("Not Used")

    override fun searchMangaSelector() = throw UnsupportedOperationException("Not Used")

    override fun searchMangaFromElement(element: Element) = throw UnsupportedOperationException("Not Used")

    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException("Not Used")
}
