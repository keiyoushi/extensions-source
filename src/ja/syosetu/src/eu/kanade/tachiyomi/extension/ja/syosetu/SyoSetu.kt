package eu.kanade.tachiyomi.extension.ja.syosetu

import eu.kanade.tachiyomi.multisrc.mangaraw.MangaRawTheme
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Evaluator

class SyoSetu : MangaRawTheme("SyoSetu", "https://syosetu.top") {
    // syosetu.top doesn't have a popular manga page redirect to latest manga request
    override fun popularMangaRequest(page: Int): Request = latestUpdatesRequest(page)

    override val supportsLatest = false

    override fun String.sanitizeTitle(): String {
        val index = lastIndexOf("RAW", ignoreCase = true)
        if (index == -1) return this
        return substring(0, index)
            .trimEnd('(', ' ', ',')
    }

    override fun popularMangaSelector() = "article"
    override fun popularMangaNextPageSelector() = ".next.page-numbers"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        GET("$baseUrl/page/$page?s=$query")

    override fun Document.getSanitizedDetails(): Element =
        selectFirst(Evaluator.Tag("article"))!!.selectFirst(Evaluator.Class("content-wrap-inner"))!!.apply {
            selectFirst(Evaluator.Class("chaplist"))!!.remove()
        }

    override fun chapterListSelector() = ".chaplist a"
    override fun String.sanitizeChapter() = substringAfterLast(" - ")

    override fun pageSelector() = Evaluator.Tag("figure")
}
