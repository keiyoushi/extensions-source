package eu.kanade.tachiyomi.extension.en.topmanhuafan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Request
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class TopManhuaFan :
    Madara(
        "TopManhua.fan",
        "https://www.topmanhua.fan",
        "en",
        dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale("en")),
    ) {
    override val mangaSubString = "manhua"
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false

    override fun chapterListSelector() = "div.wp-manga-chapter"

    // Madara sends the whole URL, we don't want that
    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headers)

    // It tries to parse it as a relative date, so it never tries to use dateFormat
    override fun chapterFromElement(element: Element): SChapter = super.chapterFromElement(element).apply {
        date_upload = parseChapterDate(element.selectFirst(chapterDateSelector())?.text())
    }
}
