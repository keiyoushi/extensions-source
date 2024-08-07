package eu.kanade.tachiyomi.extension.en.manytoon

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.nodes.Element

class ManyToon : Madara("ManyToon", "https://manytoon.com", "en") {

    override val mangaSubString = "comic"

    override val useNewChapterEndpoint = false
    override val sendViewCount = false
    override val fetchGenres = false

    override fun searchMangaSelector() = "li.movie-item > a"

    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.absUrl("href"))
            title = element.attr("title")
        }
    }

    override fun oldXhrChaptersRequest(mangaId: String): Request {
        val form = FormBody.Builder()
            .add("action", "ajax_chap")
            .add("post_id", mangaId)
            .build()

        return POST("$baseUrl/wp-admin/admin-ajax.php", xhrHeaders, form)
    }
}
