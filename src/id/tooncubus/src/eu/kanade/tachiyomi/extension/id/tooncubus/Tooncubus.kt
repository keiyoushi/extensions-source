package eu.kanade.tachiyomi.extension.id.tooncubus

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response

class Tooncubus : ZeistManga("Tooncubus", "https://www.tooncubus.top", "id") {

    override val pageListSelector = "div.check-box center"

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.asJsoup().selectFirst("ul.series-chapterlist")!!.select("div.flexch-infoz").map { element ->
            SChapter.create().apply {
                name = element.select("span").text()
                url = element.select("a").attr("href") // The website uses another domain for reading
            }
        }
    }

    override fun pageListRequest(chapter: SChapter) = GET(chapter.url, headers)

    override fun getChapterUrl(chapter: SChapter) = chapter.url
}
