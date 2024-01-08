package eu.kanade.tachiyomi.extension.en.randowiz

import eu.kanade.tachiyomi.network.GET
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
import java.text.SimpleDateFormat
import java.util.Locale

class Randowiz : ParsedHttpSource() {

    override val name = "Randowiz"

    override val baseUrl = "https://randowis.com"

    override val lang = "en"

    override val supportsLatest = false

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return Observable.just(
            MangasPage(
                listOf(
                    SManga.create().apply {
                        title = "Randowiz: We live in an MMO!?"
                        artist = "Randowiz"
                        author = "Randowiz"
                        status = SManga.ONGOING
                        url = "/category/we-live-in-an-mmo/"
                        description =
                            "The world of 'Mamuon' where players and NPC's live together in harmony. Or do they? DO THEY?"
                        thumbnail_url =
                            "https://i0.wp.com/randowis.com/wp-content/uploads/2016/02/MMO_CHP_001_CSP_000.jpg?resize=800%2C800&ssl=1"
                    },
                    SManga.create().apply {
                        title = "Randowiz: Short comics"
                        artist = "Randowiz"
                        author = "Randowiz"
                        status = SManga.ONGOING
                        url = "/category/short-comics/"
                        description =
                            "So short that i have to compensate.."
                        thumbnail_url =
                            "https://i0.wp.com/randowis.com/wp-content/uploads/2021/10/Images_PNGs_Site_BOT-SUPPORT.png"
                    },
                    SManga.create().apply {
                        title = "Randowiz: Illustations"
                        artist = "Randowiz"
                        author = "Randowiz"
                        status = SManga.ONGOING
                        url = "/category/art/"
                        description =
                            "You like draw? I give you draw."
                        thumbnail_url =
                            "https://i0.wp.com/randowis.com/wp-content/uploads/2021/05/colour-studies-021-post.jpg"
                    },
                ),
                false,
            ),
        )
    }

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> = fetchPopularManga(page).map {
        MangasPage(
            it.mangas.filter { manga ->
                manga.title.contains(
                    query,
                    ignoreCase = true,
                )
            },
            false,
        )
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = document.select(chapterListSelector())
            .map { chapterFromElement(it) }
            .toMutableList()
        var next = document.selectFirst(".next")?.attr("href") ?: ""

        while (next.isNotEmpty()) {
            val nextDocument = client.newCall(GET(next, headers)).execute().asJsoup()

            chapters += nextDocument.select(chapterListSelector())
                .map { chapterFromElement(it) }
            next = nextDocument.selectFirst(".next")?.attr("href") ?: ""
        }

        return chapters.mapIndexed { i, chapter ->
            chapter.apply { chapter_number = chapters.size.toFloat() - i }
        }
    }

    override fun chapterListSelector() = ".has-post-thumbnail"

    override fun chapterFromElement(element: Element): SChapter {
        val linkTag = element.select(".elementor-post__title a").first()!!
        val chapter = SChapter.create()
        chapter.name = linkTag.text()
        chapter.url = linkTag.attr("href").split(".com")[1]
        chapter.date_upload = parseDate(element.select(".elementor-post-date").text())
        return chapter
    }

    override fun pageListParse(document: Document): List<Page> {
        val imgs = document.select(".elementor-widget-theme-post-content img[data-permalink]")
        return imgs.mapIndexed { index, img ->
            Page(index, url = "", imageUrl = img.attr("src"))
        }
    }

    override fun imageUrlParse(document: Document) = throw Exception("Not used")

    override fun popularMangaSelector(): String = throw Exception("Not used")

    override fun searchMangaFromElement(element: Element): SManga = throw Exception("Not used")

    override fun searchMangaNextPageSelector(): String = throw Exception("Not used")

    override fun searchMangaSelector(): String = throw Exception("Not used")

    override fun popularMangaRequest(page: Int): Request = throw Exception("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        throw Exception("Not used")

    override fun popularMangaNextPageSelector(): String = throw Exception("Not used")

    override fun popularMangaFromElement(element: Element): SManga = throw Exception("Not used")

    override fun mangaDetailsParse(document: Document): SManga = throw Exception("Not used")

    override fun latestUpdatesNextPageSelector(): String = throw Exception("Not used")

    override fun latestUpdatesFromElement(element: Element): SManga = throw Exception("Not used")

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not used")

    override fun latestUpdatesSelector(): String = throw Exception("Not used")

    private fun parseDate(dateStr: String): Long {
        return runCatching { DATE_FORMATTER.parse(dateStr)?.time }.getOrNull() ?: 0L
    }

    companion object {
        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
        }
    }
}
