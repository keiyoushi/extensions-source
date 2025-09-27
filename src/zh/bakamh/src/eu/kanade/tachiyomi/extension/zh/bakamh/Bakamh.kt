package eu.kanade.tachiyomi.extension.zh.bakamh

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Headers
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Bakamh : Madara(
    "巴卡漫画",
    "https://bakamh.com",
    "zh",
    SimpleDateFormat("yyyy 年 M 月 d 日", Locale.CHINESE),
) {
    override val mangaDetailsSelectorStatus = ".post-content_item:contains(状态) .summary-content"

    override fun chapterListSelector() = "div.tab-content li:has(a[data-chapter-url])"

    override fun headersBuilder(): Headers.Builder {
        return super.headersBuilder()
            .add("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val urlElement = element.selectFirst(chapterUrlSelector)!!
        url = urlElement.attr("abs:data-chapter-url")
        name = urlElement.text()
    }
}
