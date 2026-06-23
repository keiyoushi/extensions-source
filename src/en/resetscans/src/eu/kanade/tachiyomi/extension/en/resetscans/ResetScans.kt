package eu.kanade.tachiyomi.extension.en.resetscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ResetScans :
    Madara(
        "Reset Scans",
        "https://reset-scans.org",
        "en",
        dateFormat = dateFormat,
    ) {
    // Moved from FuzzyDoodle to Madara
    override val versionId = 3

    override val useNewChapterEndpoint = true

    override fun popularMangaSelector() = ".rs-manga-library__card"
    override val popularMangaUrlSelector = popularMangaSelector() + "-title a"

    override fun searchMangaSelector() = popularMangaSelector()
    override val searchMangaUrlSelector = popularMangaUrlSelector

    override fun chapterListSelector() = "li.wp-manga-chapter:not(:has(a[href*='#']))"

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

    override fun genresRequest() = GET("$baseUrl/$mangaSubString", headers)

    override fun parseGenres(document: Document): List<Genre> = document.select("a.rs-manga-library__genre, a.rs-manga-library__genre-link").mapNotNull { a ->
        val name = a.selectFirst("span")?.text() ?: a.text()
        if (name.lowercase() == "all") return@mapNotNull null

        val url = a.absUrl("href").toHttpUrlOrNull() ?: return@mapNotNull null
        val slug = url.pathSegments.last { it.isNotEmpty() }

        Genre(name, slug)
    }
}

private val utcZone = TimeZone.getTimeZone("UTC")

private val dateFormat = SimpleDateFormat("dd-MMM", Locale.US).apply {
    timeZone = utcZone
}
