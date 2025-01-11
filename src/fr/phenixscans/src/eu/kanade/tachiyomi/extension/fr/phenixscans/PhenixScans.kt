package eu.kanade.tachiyomi.extension.fr.phenixscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class PhenixScans : MangaThemesia("PhenixScans", "https://phenixscans.fr", "fr", dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.FRENCH)) {
    override val seriesAuthorSelector = ".imptdt:contains(Auteur) i, .fmed b:contains(Auteur)+span"
    override val seriesStatusSelector = ".imptdt:contains(Statut) i"

    override fun String?.parseStatus(): Int = when {
        this == null -> SManga.UNKNOWN
        this.contains("En Cours", ignoreCase = true) -> SManga.ONGOING
        this.contains("Terminé", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun mangaDetailsParse(document: Document): SManga =
        super.mangaDetailsParse(document).apply {
            status = document.select(seriesStatusSelector).text().parseStatus()
        }

    private val chapterUrlSelector = "a"
    private val chapterNameSelector = ".chapternum"
    private val chapterDateSelector = ".chapterdate"
    private val chapterDateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.FRENCH)

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        with(element) {
            selectFirst(chapterUrlSelector)?.let { urlElement: Element ->
                chapter.url = urlElement.attr("abs:href")
                chapter.name = urlElement.selectFirst(chapterNameSelector)?.text() ?: ""
            }

            val dateString = selectFirst(chapterDateSelector)?.text()
            if (!dateString.isNullOrEmpty()) {
                try {
                    val parsedDate = chapterDateFormat.parse(dateString)?.time ?: 0
                    val currentDate = System.currentTimeMillis()

                    if (parsedDate > currentDate) {
                        chapter.date_upload = 0L
                    } else {
                        chapter.date_upload = parsedDate
                    }
                } catch (e: ParseException) {
                    chapter.date_upload = 0L
                }
            } else {
                chapter.date_upload = 0L
            }
        }

        return chapter
    }
}
