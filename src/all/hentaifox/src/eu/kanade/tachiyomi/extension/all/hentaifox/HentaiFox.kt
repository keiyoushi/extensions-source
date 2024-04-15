package eu.kanade.tachiyomi.extension.all.hentaifox

import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.HttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class HentaiFox(
    lang: String = "all",
    override val mangaLang: String = "",
) : GalleryAdults("HentaiFox", "https://hentaifox.com", lang) {

    //    override val id since we might change its lang
    override val supportsLatest = mangaLang.isNotBlank()

    override fun Element.mangaLang() = attr("data-languages")
        .split(' ').let {
            when {
                it.contains("11") -> "korean"
                it.contains("6") -> "chinese"
                it.contains("5") -> "japanese"
                else -> "english"
            }
        }

    /* Popular */
    override fun popularMangaRequest(page: Int) =
        when {
            supportsLatest -> GET("$baseUrl/language/$mangaLang/popular/pag/$page/")
            page == 2 -> GET("$baseUrl/page/$page/", headers)
            else -> GET("$baseUrl/pag/$page/", headers)
        }

    /* Latest */
    override fun latestUpdatesRequest(page: Int) =
        if (supportsLatest) {
            GET("$baseUrl/language/$mangaLang/pag/$page/")
        } else {
            throw UnsupportedOperationException()
        }

    /* Search */
    override fun tagPageUri(url: HttpUrl.Builder, page: Int) =
        url.apply {
            addPathSegments("pag/$page/")
        }

    /* Pages */
    override fun pageListRequest(document: Document): List<Page> {
        val pageUrls = document.select("$pageSelector a")
            .map { it.absUrl("href") }
            .toMutableList()

        // input only exists if pages > 10 and have to make a request to get the other thumbnails
        val totalPages = document.inputIdValueOf(totalPagesSelector)

        if (totalPages.isNotEmpty()) {
            val form = FormBody.Builder()
                .add("u_id", document.inputIdValueOf(galleryIdSelector))
                .add("g_id", document.inputIdValueOf(loadIdSelector))
                .add("img_dir", document.inputIdValueOf(loadDirSelector))
                .add("visible_pages", "10")
                .add("total_pages", totalPages)
                .add("type", "2") // 1 would be "more", 2 is "all remaining"
                .build()

            val xhrHeaders = headers.newBuilder()
                .add("X-Requested-With", "XMLHttpRequest")
                .build()

            client.newCall(POST("$baseUrl/includes/thumbs_loader.php", xhrHeaders, form))
                .execute()
                .asJsoup()
                .select("a")
                .mapTo(pageUrls) { it.absUrl("href") }
        }
        return pageUrls.mapIndexed { i, url -> Page(i, url) }
    }

    override fun imageUrlParse(document: Document): String {
        return document.selectFirst("img#gimg")?.imgAttr()!!
    }
}
