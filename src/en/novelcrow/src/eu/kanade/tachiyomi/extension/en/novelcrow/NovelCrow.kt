package eu.kanade.tachiyomi.extension.en.novelcrow

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request
import org.jsoup.nodes.Element

class NovelCrow : Madara("NovelCrow", "https://novelcrow.com", "en") {

    override val useNewChapterEndpoint = true

    override val mangaSubString = "comic"

    override val chapterUrlSuffix = ""

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/${searchPage(page)}?s=&post_type=wp-manga&m_orderby=trending", headers)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/${searchPage(page)}?s=&post_type=wp-manga&m_orderby=latest", headers)

    override fun searchMangaSelector() = popularMangaSelector()

    override fun imageFromElement(element: Element): String? {
        val image = super.imageFromElement(element)?.trim()

        if (image.isNullOrEmpty()) {
            val url = element.attr("data-src").trim().takeIf { it.isNotEmpty() }
                ?: element.attr("data-lazy-src").trim().takeIf { it.isNotEmpty() }
                ?: element.attr("src").trim().takeIf { it.isNotEmpty() }

            if (url != null) {
                return if (url.startsWith("http") || url.startsWith("data:")) {
                    url
                } else {
                    if (url.startsWith("/")) baseUrl + url else "$baseUrl/$url"
                }
            }
        }

        return image
    }
}
