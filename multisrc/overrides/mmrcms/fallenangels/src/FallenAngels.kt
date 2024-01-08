package eu.kanade.tachiyomi.extension.en.fallenangels

import eu.kanade.tachiyomi.multisrc.mmrcms.MMRCMS
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class FallenAngels : MMRCMS("Fallen Angels", "https://manga.fascans.com", "en") {

    /**
     * Returns a chapter from the given element.
     *
     * @param element an element obtained from [chapterListSelector].
     */
    override fun nullableChapterFromElement(element: Element): SChapter? {
        val chapter = SChapter.create()

        val titleWrapper = element.select("[class^=chapter-title-rtl]").first()!!
        val chapterElement = titleWrapper.getElementsByTag("a")!!
        val url = chapterElement.attr("href")

        chapter.url = getUrlWithoutBaseUrl(url)

        // Construct chapter names
        // before -> <mangaName> <chapterNumber> : <chapterTitle>
        // after  -> Chapter     <chapterNumber> : <chapterTitle>
        val chapterText = chapterElement.text()
        val numberRegex = Regex("""[1-9]\d*(\.\d+)*""")
        val chapterNumber = numberRegex.find(chapterText)?.value.orEmpty()
        val chapterTitle = titleWrapper.getElementsByTag("em")!!.text()
        chapter.name = "Chapter $chapterNumber : $chapterTitle"

        // Parse date
        val dateText = element.getElementsByClass("date-chapter-title-rtl").text().trim()
        val dateFormat = SimpleDateFormat("d MMM. yyyy", Locale.US)
        chapter.date_upload = dateFormat.parse(dateText)?.time ?: 0L

        return chapter
    }
}
