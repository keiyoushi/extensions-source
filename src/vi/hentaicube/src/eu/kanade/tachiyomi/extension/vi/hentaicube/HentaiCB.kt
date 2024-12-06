package eu.kanade.tachiyomi.extension.vi.hentaicube

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Request
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class HentaiCB : Madara("CBHentai", "https://hentaicube.xyz", "vi", SimpleDateFormat("dd/MM/yyyy", Locale("vi"))) {

    override val id: Long = 823638192569572166

    override val mangaSubString = "read"

    override val filterNonMangaItems = false

    override fun popularMangaRequest(page: Int): Request =
        if (useLoadMoreRequest()) {
            loadMoreRequest(page, popular = true)
        } else {
            GET("$baseUrl/$mangaSubString/${searchPage(page)}?s&post_type=wp-manga&m_orderby=views", headers)
        }

    override fun latestUpdatesRequest(page: Int): Request =
        if (useLoadMoreRequest()) {
            loadMoreRequest(page, popular = false)
        } else {
            GET("$baseUrl/$mangaSubString/${searchPage(page)}?s&post_type=wp-manga&m_orderby=latest", headers)
        }

    override fun pageListParse(document: Document): List<Page> {
        return super.pageListParse(document).distinctBy { it.imageUrl }
    }
}
