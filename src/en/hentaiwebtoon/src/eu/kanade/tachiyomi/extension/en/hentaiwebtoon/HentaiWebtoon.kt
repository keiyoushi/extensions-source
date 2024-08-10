package eu.kanade.tachiyomi.extension.en.hentaiwebtoon

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class HentaiWebtoon : Madara("HentaiWebtoon", "https://hentaiwebtoon.com", "en") {

    // The website does not flag the content.
    override val filterNonMangaItems = false
    override val useNewChapterEndpoint = false
    override val sendViewCount = false
    override val fetchGenres = false
    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override fun popularMangaNextPageSelector() = "a.next"
    override fun searchMangaSelector() = "li.movie-item > a"
    override fun searchMangaNextPageSelector() = "a.next"

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

    override fun pageListParse(document: Document) = super.pageListParse(document).onEach {
        it.imageUrl = it.imageUrl?.replace("http://", "https://")
    }
}
