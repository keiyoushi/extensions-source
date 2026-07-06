package eu.kanade.tachiyomi.extension.en.tasho

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@Source
abstract class Tasho : Madara() {

    override val dateFormat: SimpleDateFormat = SimpleDateFormat("dd-MMM", Locale.US).apply {
        timeZone = utcZone
    }

    override val useNewChapterEndpoint = true

    // The site uses a highly customized "zaxreader" child theme layout that breaks standard AJAX
    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override fun popularMangaSelector() = "article.rs-manga-library__card"
    override val popularMangaUrlSelector = ".rs-manga-library__card-title a"

    override fun popularMangaNextPageSelector() = "a.next.page-numbers, div.nav-previous, nav.navigation-ajax, a.nextwords"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga/${searchPage(page)}?m_orderby=trending", headers)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga/${searchPage(page)}?m_orderby=new-manga", headers)

    // Search and Latest use the same grid as Popular
    override fun searchMangaSelector() = popularMangaSelector()

    override val mangaDetailsSelectorGenre = "div.post-content_item:has(h5:contains(Genre)) .summary-content a"
    override val mangaDetailsSelectorStatus = "div.post-content_item:has(h5:contains(Status)) .summary-content"

    override fun chapterListSelector() = "li.wp-manga-chapter:not(:has(a[href*='#']))"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = super.chapterFromElement(element)

        // For new chapters, the date text is empty and tucked into the "title" attribute
        if (chapter.date_upload == 0L) {
            val dateElement = element.selectFirst("span.chapter-release-date a, span.chapter-release-date i")
            val dateText = dateElement?.let {
                if (it.tagName() == "a" && it.hasAttr("title")) {
                    it.attr("title")
                } else {
                    it.text()
                }
            }

            if (!dateText.isNullOrBlank()) {
                chapter.date_upload = parseChapterDate(dateText)
            }
        }

        return chapter
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = super.chapterListParse(response)

        var currentYear = Calendar.getInstance(utcZone).get(Calendar.YEAR)
        var previousMonth = -1

        for (chapter in chapters) {
            if (chapter.date_upload > 0L) {
                val cal = Calendar.getInstance(utcZone)
                cal.timeInMillis = chapter.date_upload

                // 1970 means the date was parsed without a year
                if (cal.get(Calendar.YEAR) == 1970) {
                    val month = cal.get(Calendar.MONTH)

                    if (previousMonth != -1) {
                        // Month jumping forward (e.g. Jan to Dec) means we crossed into the previous year
                        if (month - previousMonth >= 6) {
                            currentYear--
                        }
                    } else {
                        // If the first parsed date is in the future (+7 day buffer), it belongs to last year
                        cal.set(Calendar.YEAR, currentYear)
                        if (cal.timeInMillis > System.currentTimeMillis() + 604_800_000L) {
                            currentYear--
                        }
                    }

                    cal.set(Calendar.YEAR, currentYear)
                    chapter.date_upload = cal.timeInMillis

                    previousMonth = month
                } else {
                    // Update tracking variables using dates that already have a valid year
                    currentYear = cal.get(Calendar.YEAR)
                    previousMonth = cal.get(Calendar.MONTH)
                }
            }
        }

        return chapters
    }

    override fun parseGenres(document: Document): List<Genre> {
        return document.select("a.rs-manga-library__genre, a.rs-manga-library__genre-link").mapNotNull { a ->
            val name = a.selectFirst("span")?.text() ?: a.text()
            if (name.equals("all", ignoreCase = true)) return@mapNotNull null

            val url = a.absUrl("href").toHttpUrlOrNull() ?: return@mapNotNull null
            val slug = url.pathSegments.lastOrNull { it.isNotEmpty() } ?: return@mapNotNull null

            Genre(name, slug)
        }
    }
}

private val utcZone = TimeZone.getTimeZone("UTC")
