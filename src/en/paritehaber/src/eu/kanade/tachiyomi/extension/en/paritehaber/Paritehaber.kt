package eu.kanade.tachiyomi.extension.en.paritehaber

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import org.jsoup.nodes.Element

class Paritehaber :
    Madara(
        "Paritehaber",
        "https://www.paritehaber.com",
        "en",
    ) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false

    override val fetchGenres = false
    override val chapterUrlSuffix = ""

    override fun popularMangaRequest(page: Int): Request {
        val pagePath = if (page > 1) "page/$page/" else ""
        return GET("$baseUrl/$pagePath?s=&post_type=wp-manga&m_orderby=trending", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val pagePath = if (page > 1) "page/$page/" else ""
        return GET("$baseUrl/$pagePath?s=&post_type=wp-manga&m_orderby=latest", headers)
    }

    override fun popularMangaSelector() = searchMangaSelector()
    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)
}
