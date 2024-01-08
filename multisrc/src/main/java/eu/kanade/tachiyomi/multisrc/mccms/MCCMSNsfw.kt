package eu.kanade.tachiyomi.multisrc.mccms

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.select.Evaluator

open class MCCMSNsfw(
    name: String,
    baseUrl: String,
    lang: String = "zh",
) : MCCMSWeb(name, baseUrl, lang, hasCategoryPage = false) {

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        if (query.isNotBlank()) {
            GET("$baseUrl/search/$query/$page", pcHeaders)
        } else {
            super.searchMangaRequest(page, query, filters)
        }

    override fun searchMangaParse(response: Response) = parseListing(response.asJsoup())

    override fun pageListRequest(chapter: SChapter): Request =
        GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val container = response.asJsoup().selectFirst(Evaluator.Class("comic-list"))!!
        return container.select(Evaluator.Tag("img")).mapIndexed { index, img ->
            Page(index, imageUrl = img.attr("src"))
        }
    }
}
