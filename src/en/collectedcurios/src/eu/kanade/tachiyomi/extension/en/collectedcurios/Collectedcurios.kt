package eu.kanade.tachiyomi.extension.en.collectedcurios

import android.util.Log
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class Collectedcurios : ParsedHttpSource() {

    override val name = "Collected Curios"

    override val baseUrl = "https://www.collectedcurios.com"

    override val lang = "en"

    override val supportsLatest = false

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return Observable.just(
            MangasPage(
                arrayListOf(
                    SManga.create().apply {
                        title = "Sequential Art"
                        artist = "Jolly Jack aka Phillip M Jackson"
                        author = "Jolly Jack aka Phillip M Jackson"
                        status = SManga.ONGOING
                        url = "/sequentialart.php"
                        description = "Sequential Art webcomic."
                        thumbnail_url = "https://www.collectedcurios.com/images/CC_2011_Sequential_Art_Button.jpg"
                    },

                    SManga.create().apply {
                        title = "Battle Bunnies"
                        artist = "Jolly Jack aka Phillip M Jackson"
                        author = "Jolly Jack aka Phillip M Jackson"
                        status = SManga.ONGOING
                        url = "/battlebunnies.php"
                        description = "Battle Bunnies webcomic."
                        thumbnail_url = "https://www.collectedcurios.com/images/CC_2011_Battle_Bunnies_Button.jpg"
                    },

                    /*
                    SManga.create().apply {
                        title = "Spider and Scorpion"
                        artist = "Jolly Jack aka Phillip M Jackson"
                        author = "Jolly Jack aka Phillip M Jackson"
                        status = SManga.ONGOING
                        url = "/spiderandscorpion.php"
                        description = "Spider and Scorpion webcomic."
                        thumbnail_url = "https://www.collectedcurios.com/images/CC_2011_Spider_And_Scorpion_Button.jpg"
                    },
                    */
                ),
                false,
            ),
        )
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return fetchPopularManga(1)
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return Observable.just(manga)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val responseJs = response.asJsoup()

        val chapters =
            if (responseJs.selectFirst("img[title=Last]") == null) {
                responseJs.selectFirst("input[title=Jump to number]")
                    ?.attr("value")?.toInt()
            } else {
                responseJs.selectFirst("img[title=Last]")?.parent()
                    ?.attr("href")?.substringAfter("=")?.toInt()
            }

        var chapterList = mutableListOf<SChapter>()

        for (i in chapters?.downTo(1)!!) {
            chapterList.add(
                SChapter.create().apply {
                    url = "${response.request.url}?s=$i"
                    name = "Chapter - $i"
                    chapter_number = i.toFloat()
                    date_upload = 0L
                },
            )
        }
        return chapterList
    }

    override fun chapterListSelector() = throw Exception("Not used")

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = element.selectFirst(".w3-round")?.attr("value") ?: "Chapter"
    }

    override fun pageListParse(document: Document): List<Page> = throw Exception("Not used")

    override fun fetchPageList(chapter: SChapter) = Observable.just(listOf(Page(0, chapter.url)))!!

    override fun imageUrlParse(response: Response): String {
        val url = response.request.url.toString()
        val document = response.asJsoup()
        
        return when {
            url.contains("sequentialart") ->
                document.selectFirst(".w3-image")!!.absUrl("src")
            url.contains("battlebunnies") || url.contains("spiderandscorpion") ->
                document.selectFirst("#strip")!!.absUrl("src")
            else -> throw Exception("Could not find the image")
        }
    }

    override fun imageUrlParse(document: Document) = throw Exception("Not used")

    override fun popularMangaSelector(): String = throw Exception("Not used")

    override fun searchMangaFromElement(element: Element): SManga = throw Exception("Not used")

    override fun searchMangaNextPageSelector(): String? = throw Exception("Not used")

    override fun searchMangaSelector(): String = throw Exception("Not used")

    override fun popularMangaRequest(page: Int): Request = throw Exception("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw Exception("Not used")

    override fun popularMangaNextPageSelector(): String? = throw Exception("Not used")

    override fun popularMangaFromElement(element: Element): SManga = throw Exception("Not used")

    override fun mangaDetailsParse(document: Document): SManga = throw Exception("Not used")

    override fun latestUpdatesNextPageSelector(): String? = throw Exception("Not used")

    override fun latestUpdatesFromElement(element: Element): SManga = throw Exception("Not used")

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not used")

    override fun latestUpdatesSelector(): String = throw Exception("Not used")
}
