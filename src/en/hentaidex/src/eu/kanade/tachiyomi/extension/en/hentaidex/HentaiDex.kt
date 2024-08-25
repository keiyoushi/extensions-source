package eu.kanade.tachiyomi.extension.en.hentaidex

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class HentaiDex : MangaThemesia(
    "HentaiDex",
    "https://dexhentai.com",
    "en",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US),
) {
    override fun chapterListParse(response: Response): List<SChapter> = super.chapterListParse(response).sortedByDescending { it.chapter_number }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val urlElements = element.select("a")
        setUrlWithoutDomain(urlElements.attr("href"))
        name = element.select(".chapternum").text().ifBlank { urlElements.first()!!.text() }
        chapter_number = element.attr("data-num").toFloat()
        date_upload = element.selectFirst(".chapterdate")?.text().parseChapterDate()
    }
}
