package eu.kanade.tachiyomi.extension.fr.mangafr

import eu.kanade.tachiyomi.multisrc.mmrcms.MMRCMS
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MangaFR : MMRCMS("Manga-FR", "https://manga-fr.cc", "fr") {
    override fun mangaDetailsParse(response: Response): SManga {
        return super.mangaDetailsParse(response).apply {
            title = title.replace("Chapitres ", "")
        }
    }

    override fun nullableChapterFromElement(element: Element): SChapter? {
        val chapter = SChapter.create()

        val titleWrapper = element.select("[class^=chapter-title-rtl]").first()!!
        val chapterElement = titleWrapper.getElementsByTag("a")!!
        val url = chapterElement.attr("href")

        chapter.url = getUrlWithoutBaseUrl(url)

        // Construct chapter names
        // Before -> Scan <manga_name> <chapter_number> VF: <chapter_number>
        // Now    -> Chapitre <chapter_number> : <chapter_title> OR Chapitre <chapter_number>
        val chapterText = chapterElement.text()
        val numberRegex = Regex("""[1-9]\d*(\.\d+)*""")
        val chapterNumber = numberRegex.find(chapterText)?.value.orEmpty()
        val chapterTitle = titleWrapper.getElementsByTag("em")!!.text()
        if (chapterTitle.toIntOrNull() != null) {
            chapter.name = "Chapitre $chapterNumber"
        } else {
            chapter.name = "Chapitre $chapterNumber : $chapterTitle"
        }

        // Parse date
        val dateText = element.getElementsByClass("date-chapter-title-rtl").text().trim()

        chapter.date_upload = runCatching {
            dateFormat.parse(dateText)?.time
        }.getOrNull() ?: 0L

        return chapter
    }

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("d MMM. yyyy", Locale.US)
        }
    }
}
