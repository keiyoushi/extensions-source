package eu.kanade.tachiyomi.extension.es.ragnarokscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.WordSet
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class RagnarokScan : Madara(
    "RagnarokScan",
    "https://ragnarokscan.com",
    "es",
    SimpleDateFormat("MMMMM dd, yyyy", Locale("es")),
) {
    // "mangaSubstring" has changed, so users will have to migrate
    override val versionId = 2
    override val mangaSubString = "series"
    override val chapterUrlSuffix = ""

    override fun popularMangaSelector() = "div#series-card:has(a:not([href*='bilibilicomics.com']))"
    override val popularMangaUrlSelector = "a.series-link"

    override val mangaDetailsSelectorTag = "div.tags-content a.notUsed" // Source use this for the scanlator
    override val mangaDetailsSelectorStatus = "div.post-status div.summary-content"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        with(element) {
            select(popularMangaUrlSelector).first()?.let {
                manga.setUrlWithoutDomain(it.attr("abs:href"))
                manga.title = it.attr("title")
            }

            select("img").first()?.let {
                manga.thumbnail_url = imageFromElement(it)
            }
        }

        return manga
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        with(element) {
            select(chapterUrlSelector).first()?.let { urlElement ->
                chapter.url = urlElement.attr("abs:href").let {
                    it.substringBefore("?style=paged") + if (!it.endsWith(chapterUrlSuffix)) chapterUrlSuffix else ""
                }
                chapter.name = urlElement.select("p.chapter-manhwa-title").text()
                chapter.date_upload = parseChapterDate(select("span.chapter-release-date").text())
            }
        }

        return chapter
    }

    override fun parseChapterDate(date: String?): Long {
        date ?: return 0

        fun SimpleDateFormat.tryParse(string: String): Long {
            return try {
                parse(string)?.time ?: 0
            } catch (_: ParseException) {
                0
            }
        }

        return when {
            WordSet("minuto", "minutos", "hora", "horas", "día", "días").endsWith(date) -> {
                parseRelativeDate(date)
            }
            else -> SimpleDateFormat("MMMMM dd, yyyy", Locale("es")).tryParse(date)
        }
    }
}
