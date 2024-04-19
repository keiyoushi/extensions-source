package eu.kanade.tachiyomi.extension.all.hentaifox

import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
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

    override fun HttpUrl.Builder.addPageUri(page: Int): HttpUrl.Builder {
        val url = toString()
        when {
            url == "$baseUrl/" && page == 2 ->
                addPathSegments("page/$page")
            url.contains('?') ->
                addQueryParameter("page", page.toString())
            page > 1 ->
                addPathSegments("pag/$page")
        }
        addPathSegment("") // trailing slash (/)
        return this
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

    override fun buildQueryString(tags: List<String>, query: String): String {
        return (tags + query).filterNot { it.isBlank() }.joinToString("+") {
            // replace any special character
            it.trim().replace(Regex("""[^a-zA-Z0-9"]+"""), "+")
        }
    }

    override fun getFilterList() = FilterList(
        listOf(
            Filter.Header("HINT: Use double quote (\") for exact match"),
        ) + super.getFilterList().list,
    )
}
