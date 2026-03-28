package eu.kanade.tachiyomi.extension.en.collectedcurios

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class Collectedcurios : HttpSource() {

    override val name = "Collected Curios"

    override val baseUrl = "https://www.collectedcurios.com"

    override val lang = "en"

    override val supportsLatest = false

    // ============================== Popular ===============================
    override fun fetchPopularManga(page: Int): Observable<MangasPage> = Observable.just(
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

                SManga.create().apply {
                    title = "Spider and Scorpion"
                    artist = "Jolly Jack aka Phillip M Jackson"
                    author = "Jolly Jack aka Phillip M Jackson"
                    status = SManga.ONGOING
                    url = "/spiderandscorpion.php"
                    description = "Spider and Scorpion webcomic."
                    thumbnail_url = "https://www.collectedcurios.com/images/CC_2011_Spider_And_Scorpion_Button.jpg"
                },
            ),
            false,
        ),
    )

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // =============================== Search ===============================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = fetchPopularManga(1)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // =========================== Manga Details ============================
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga)

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    // ============================== Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val responseJs = response.asJsoup()

        val chapters = responseJs.selectFirst("img[title=Last]")?.parent()
            ?.attr("href")?.substringAfter("=")?.toIntOrNull()
            ?: responseJs.selectFirst("input[title=Jump to number]")
                ?.attr("value")?.toIntOrNull()
            ?: responseJs.selectFirst("img[title=Back one]")?.parent()
                ?.attr("href")?.substringAfter("=")?.toIntOrNull()?.plus(1)
            ?: 1

        val chapterList = mutableListOf<SChapter>()
        val requestPath = response.request.url.encodedPath

        for (i in chapters downTo 1) {
            chapterList.add(
                SChapter.create().apply {
                    url = "$requestPath?s=$i"
                    name = "Chapter - $i"
                    chapter_number = i.toFloat()
                    date_upload = 0L
                },
            )
        }
        return chapterList
    }

    // =============================== Pages ================================
    override fun fetchPageList(chapter: SChapter) = Observable.just(listOf(Page(0, baseUrl + chapter.url)))

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

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
}
