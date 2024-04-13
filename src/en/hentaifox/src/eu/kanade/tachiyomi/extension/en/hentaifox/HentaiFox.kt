package eu.kanade.tachiyomi.extension.en.hentaifox

import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

open class HentaiFox(
    lang: String = "all",
    final override val mangaLang: String = "",
) : GalleryAdults("HentaiFox", "https://hentaifox.com", lang) {

    //    override val id since we might change its lang
    override val supportsLatest = mangaLang.isNotBlank()

    override fun Element.mangaLang() = attr("data-languages")
        .split(' ').let {
            when {
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
    override val favoritePath = "profile"

    override fun tagPageUri(url: HttpUrl.Builder, page: Int) =
        url.apply {
            addPathSegments("pag/$page/")
        }

    /* Chapters */
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return listOf(
            SChapter.create().apply {
                name = "Chapter"
                scanlator = document.selectFirst(mangaDetailInfoSelector)?.getTag("Groups")
                // page path with a marker at the end
                url = "${response.request.url.toString().replace("/gallery/", "/g/")}#"
                // number of pages
                url += document.select("[id=load_pages]").attr("value")
            },
        )
    }

    /* Pages */
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        // split the "url" to get the page path and number of pages
        return chapter.url.split("#").let { list ->
            Observable.just(listOf(1..list[1].toInt()).flatten().map { Page(it, list[0] + "$it/") })
        }
    }

    override fun imageUrlParse(document: Document): String {
        return document.selectFirst("img#gimg")?.imgAttr()!!
    }

    override fun pageListParse(document: Document): List<Page> = throw UnsupportedOperationException()
}
