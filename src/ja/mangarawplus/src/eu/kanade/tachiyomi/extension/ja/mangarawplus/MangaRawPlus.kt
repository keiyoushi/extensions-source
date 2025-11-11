package eu.kanade.tachiyomi.extension.ja.mangarawplus

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import org.jsoup.nodes.Element

class MangaRawPlus : Madara("MANGARAW+", "https://mangarawx.net", "ja") {

    override val mangaSubString = "threads"

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/?s&post_type=wp-manga&m_orderby=views", headers)

    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/?s&post_type=wp-manga&m_orderby=latest", headers)

    override fun popularMangaFromElement(element: Element): SManga {
        return super.popularMangaFromElement(element).apply {
            thumbnail_url = thumbnail_url?.replaceFirst("-193x278", "")
        }
    }

    override fun imageFromElement(element: Element): String? {
        return when {
            element.hasAttr("data-src-img") -> element.absUrl("data-src-img")
            else -> super.imageFromElement(element)
        }
    }

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder().apply {
            removeAll("Referer")
        }.build()
        return GET(page.imageUrl!!, imgHeaders)
    }

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false
}
