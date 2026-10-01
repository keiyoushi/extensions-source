package eu.kanade.tachiyomi.extension.en.irovedout

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class IRovedOut : KeiSource() {

    override val supportsLatest = false
    private val archiveUrl get() = "$baseUrl/archive"
    private val thumbnailUrl = "https://i.ibb.co/2g7Htwq/irovedout.png"
    private val seriesTitle = "I Roved Out in Search of Truth and Love"
    private val authorName = "Alexis Flower"
    private val seriesGenre = "Fantasy"
    private val seriesDescription = """
        I ROVED OUT IN SEARCH OF TRUTH AND LOVE is written & illustrated by Alexis Flower.
        It updates in chunks anywhere between 3 and 30 pages long at least once a month.
    """.trimIndent()
    private val titleRegex = Regex("Book (\\d+): (.+)")

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchChapters) return SMangaUpdate(manga, chapters)

        val mainPage = client.get(baseUrl).asJsoup()
        val books = mainPage.select("#menu-menu > li > a[href^=$archiveUrl]")

        var chapterCounter = 1F
        val chaptersByBook = books.mapIndexed { bookIndex, book ->
            val bookNumber = bookIndex + 1
            val bookUrl = book.attr("href")
            val bookPage = client.get(bookUrl).asJsoup()
            val chapterWraps = bookPage.select(".comic-archive-chapter-wrap")
            chapterWraps.map {
                val chapterWrap = it.selectFirst(".comic-archive-chapter-wrap")!!
                SChapter.create().apply {
                    name = "Book $bookNumber: ${chapterWrap.selectFirst(".comic-archive-chapter")!!.text()}"
                    url = chapterWrap.selectFirst(".comic-archive-title > a")!!.attr("href")
                    date_upload = dateFormat.tryParseDate(chapterWrap.select(".comic-archive-date").last()!!.text())
                    chapter_number = chapterCounter++
                }
            }
        }
        return SMangaUpdate(manga, chaptersByBook.flatten().reversed())
    }

    override suspend fun getImageUrl(page: Page): String {
        val comicPage = client.get(page.url).asJsoup()
        return comicPage.selectFirst("#comic img")!!.attr("src")
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val match = titleRegex.matchEntire(chapter.name) ?: return listOf()
        val bookNumber = match.groups[1]!!.value.toInt()
        val title = match.groups[2]!!.value
        val bookPage = client.get(archiveUrl + if (bookNumber != 1) "-book-$bookNumber" else "").asJsoup()
        val chapterWrap = bookPage.select(".comic-archive-chapter-wrap").find { it.selectFirst(".comic-archive-chapter")!!.text() == title }
        val pageUrls = chapterWrap?.select(".comic-archive-list-wrap .comic-archive-title > a")?.map { it.attr("href") } ?: return listOf()
        return pageUrls.mapIndexed { pageIndex, pageUrl ->
            Page(pageIndex, pageUrl)
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val manga = SManga.create().apply {
            url = ""
            thumbnail_url = thumbnailUrl
            title = seriesTitle
            author = authorName
            artist = authorName
            description = seriesDescription
            genre = seriesGenre
            status = SManga.ONGOING
            initialized = true
        }
        return MangasPage(listOf(manga), false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = throw UnsupportedOperationException()
}

private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
