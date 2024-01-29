package eu.kanade.tachiyomi.extension.ar.olaoe

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Request
import org.jsoup.nodes.Element

class Olaoe : Madara("Olaoe", "https://olaoe.cyou", "ar") {
    override val mangaSubString = "works"
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/page/$page/?m_orderby=views", headers)
    override fun chapterListSelector() = "li.wp-manga-chapter:not(.premium-block)" // Filter fake chapters
    override fun chapterFromElement(element: Element): SChapter {
        val manga = dto.data.map {
            SManga.create().apply {
                setUrlWithoutDomain(it.url)
                title = it.title
            }
        }
        return chapter
    }
}
