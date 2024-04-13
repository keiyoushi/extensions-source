package eu.kanade.tachiyomi.extension.en.hentaifox

import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
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

    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException()

    // Pages

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

    // Filters

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        GenreFilter(),
    )

    // Top 50 tags
    private class GenreFilter : UriPartFilter(
        "Category",
        arrayOf(
            Pair("<select>", "---"),
            Pair("Big breasts", "big-breasts"),
            Pair("Sole female", "sole-female"),
            Pair("Sole male", "sole-male"),
            Pair("Anal", "anal"),
            Pair("Nakadashi", "nakadashi"),
            Pair("Group", "group"),
            Pair("Stockings", "stockings"),
            Pair("Blowjob", "blowjob"),
            Pair("Schoolgirl uniform", "schoolgirl-uniform"),
            Pair("Rape", "rape"),
            Pair("Lolicon", "lolicon"),
            Pair("Glasses", "glasses"),
            Pair("Defloration", "defloration"),
            Pair("Ahegao", "ahegao"),
            Pair("Incest", "incest"),
            Pair("Shotacon", "shotacon"),
            Pair("X-ray", "x-ray"),
            Pair("Bondage", "bondage"),
            Pair("Full color", "full-color"),
            Pair("Double penetration", "double-penetration"),
            Pair("Femdom", "femdom"),
            Pair("Milf", "milf"),
            Pair("Yaoi", "yaoi"),
            Pair("Multi-work series", "multi-work-series"),
            Pair("Schoolgirl", "schoolgirl"),
            Pair("Mind break", "mind-break"),
            Pair("Paizuri", "paizuri"),
            Pair("Mosaic censorship", "mosaic-censorship"),
            Pair("Impregnation", "impregnation"),
            Pair("Males only", "males-only"),
            Pair("Sex toys", "sex-toys"),
            Pair("Sister", "sister"),
            Pair("Dark skin", "dark-skin"),
            Pair("Ffm threesome", "ffm-threesome"),
            Pair("Hairy", "hairy"),
            Pair("Cheating", "cheating"),
            Pair("Sweating", "sweating"),
            Pair("Yuri", "yuri"),
            Pair("Netorare", "netorare"),
            Pair("Full censorship", "full-censorship"),
            Pair("Schoolboy uniform", "schoolboy-uniform"),
            Pair("Dilf", "dilf"),
            Pair("Big penis", "big-penis"),
            Pair("Futanari", "futanari"),
            Pair("Swimsuit", "swimsuit"),
            Pair("Collar", "collar"),
            Pair("Uncensored", "uncensored"),
            Pair("Big ass", "big-ass"),
            Pair("Story arc", "story-arc"),
            Pair("Teacher", "teacher"),
        ),
    )

    private open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
