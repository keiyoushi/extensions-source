package eu.kanade.tachiyomi.extension.en.readgojo

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Element

class ReadGojo : MangaThemesia("ReadGojo", "https://readgojo.com", "en") {

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))

        name = element.selectFirst(".truncate p")?.text()!!.ifBlank { element.text() }
        date_upload = element.select("p").last()?.text()!!.split("Released ").last().parseChapterDate()
    }

    override fun chapterListSelector() = "#chapterlist > a"

    protected override fun String?.parseChapterDate(): Long {
        if (this == null) return 0
        return try {
            dateFormat.parse(this)?.time ?: 0
        } catch (_: Exception) {
            0
        }
    }
}
