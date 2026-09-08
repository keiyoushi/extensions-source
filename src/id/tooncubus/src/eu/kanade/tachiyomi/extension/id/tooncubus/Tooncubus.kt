package eu.kanade.tachiyomi.extension.id.tooncubus

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document

@Source
abstract class Tooncubus : ZeistManga() {

    override val pageListSelector = "div.check-box center"

    override val supportsChapterFeed = false

    override suspend fun getChapterList(feedUrl: String, doc: Document?): List<SChapter> = doc!!.selectFirst("ul.series-chapterlist")!!.select("div.flexch-infoz").map { element ->
        SChapter.create().apply {
            name = element.select("span").text()
            url = element.select("a").attr("href") // The website uses another domain for reading
        }
    }

    override fun getChapterUrl(chapter: SChapter) = chapter.url
}
