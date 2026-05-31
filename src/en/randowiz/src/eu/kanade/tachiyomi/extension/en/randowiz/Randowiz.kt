package eu.kanade.tachiyomi.extension.en.randowiz

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class Randowiz : HttpSource() {

    override val name = "Randowiz"

    override val baseUrl = "https://randowis.com"

    override val lang = "en"

    override val supportsLatest = false

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = Observable.just(
        MangasPage(
            listOf(
                SManga.create().apply {
                    title = "Randowiz: We live in an MMO!?"
                    artist = "Randowiz"
                    author = "Randowiz"
                    status = SManga.ONGOING
                    url = "/category/we-live-in-an-mmo/"
                    description = "The world of 'Mamuon' where players and NPC's live together in harmony. Or do they? DO THEY?"
                    thumbnail_url = "https://i0.wp.com/randowis.com/wp-content/uploads/2016/02/MMO_CHP_001_CSP_000.jpg?resize=800%2C800&ssl=1"
                },
                SManga.create().apply {
                    title = "Randowiz: Short comics"
                    artist = "Randowiz"
                    author = "Randowiz"
                    status = SManga.ONGOING
                    url = "/category/short-comics/"
                    description = "So short that i have to compensate.."
                    thumbnail_url = "https://i0.wp.com/randowis.com/wp-content/uploads/2021/10/Images_PNGs_Site_BOT-SUPPORT.png"
                },
                SManga.create().apply {
                    title = "Randowiz: Illustations"
                    artist = "Randowiz"
                    author = "Randowiz"
                    status = SManga.ONGOING
                    url = "/category/art/"
                    description = "You like draw? I give you draw."
                    thumbnail_url = "https://i0.wp.com/randowis.com/wp-content/uploads/2021/05/colour-studies-021-post.jpg"
                },
            ),
            false,
        ),
    )

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> = fetchPopularManga(page).map {
        MangasPage(
            it.mangas.filter { manga ->
                manga.title.contains(query, ignoreCase = true)
            },
            false,
        )
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var currentDocument = response.asJsoup()

        while (true) {
            chapters += currentDocument.select(".has-post-thumbnail").map { element ->
                SChapter.create().apply {
                    val linkTag = element.selectFirst(".elementor-post__title a")!!
                    name = linkTag.text()
                    setUrlWithoutDomain(linkTag.attr("abs:href"))
                    date_upload = dateFormat.tryParse(element.selectFirst(".elementor-post-date")?.text())
                }
            }

            val nextUrl = currentDocument.selectFirst(".next")?.attr("abs:href")
            if (nextUrl.isNullOrEmpty()) break

            currentDocument = client.newCall(GET(nextUrl, headers)).execute().asJsoup()
        }

        return chapters.mapIndexed { i, chapter ->
            chapter.apply { chapter_number = chapters.size.toFloat() - i }
        }
    }

    override fun pageListParse(response: Response): List<Page> = response.asJsoup().select(".elementor-widget-theme-post-content img").mapIndexed { index, img ->
        Page(index, imageUrl = img.attr("abs:src"))
    }

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
    }
}
