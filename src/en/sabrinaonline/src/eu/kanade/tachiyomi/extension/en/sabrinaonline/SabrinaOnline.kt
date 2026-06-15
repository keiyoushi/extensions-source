package eu.kanade.tachiyomi.extension.en.sabrinaonline

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable

class SabrinaOnline : HttpSource() {

    override val name = "Sabrina Online"
    override val baseUrl = "https://www.sabrina-online.com"
    override val lang = "en"
    override val supportsLatest: Boolean = false

    fun manga(): SManga = SManga.create().apply {
        title = "Sabrina Online"
        thumbnail_url = "https://dummyimage.com/768x994/000/ffffff.jpg&text=$title"
        artist = "Eric W. Schwartz"
        author = "Eric W. Schwartz"
        status = SManga.UNKNOWN
        setUrlWithoutDomain("$baseUrl/archive.html")
        initialized = true
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = Observable.just(MangasPage(listOf(manga()), false))

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga())

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl/archive.html", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()

        // turn cells into chapters, with the section name if present
        fun trToChapters(tr: Element, sections: List<String?>): List<SChapter> {
            val chapters = mutableListOf<SChapter>()

            tr.select("td").forEachIndexed { index, td ->
                td.select("a").forEach { a ->
                    val chapter = a.text()
                    if (chapter.isEmpty()) return@forEach

                    val hasYear = sections.getOrNull(index)?.let { it.isNotEmpty() && it.first().isDigit() } ?: false
                    chapters.add(
                        SChapter.create().apply {
                            setUrlWithoutDomain(a.absUrl("href"))
                            name = if (hasYear) "${sections[index]} $chapter" else chapter
                        },
                    )
                }
            }

            return chapters
        }

        document.select("center table tr").chunked(2).forEach { pair ->
            if (pair.size < 2) {
                chapters.addAll(trToChapters(pair[0], listOf()))
            } else {
                val sections = pair[0].select("td").map { it.text().trim() }
                // use the section names if there are any in the first row
                if (sections.isNotEmpty()) {
                    chapters.addAll(trToChapters(pair[1], sections))
                } else {
                    chapters.addAll(trToChapters(pair[1], listOf()))
                    chapters.addAll(trToChapters(pair[0], listOf()))
                }
            }
        }

        return chapters.reversed()
    }

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pages = document.select("center img").filter {
            it.hasAttr("src") && (
                it.attr("src").contains("strips/") ||
                    it.attr("src").contains("pages/")
                )
        }

        return pages.mapIndexed { index, img ->
            // use full image instead of preview if available
            if (img.parent()?.tagName() == "a") {
                val parent = img.parent()!!
                val href = parent.absUrl("href").ifEmpty { parent.attr("href") }
                Page(index, imageUrl = href)
            } else {
                val src = img.absUrl("src").ifEmpty { img.attr("src") }
                Page(index, src)
            }
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
}
