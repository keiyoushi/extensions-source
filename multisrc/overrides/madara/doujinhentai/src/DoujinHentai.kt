package eu.kanade.tachiyomi.extension.es.doujinhentai

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class DoujinHentai : Madara(
    "DoujinHentai",
    "https://doujinhentai.net",
    "es",
    SimpleDateFormat("d MMM. yyyy", Locale.ENGLISH),
) {

    override val fetchGenres = false

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/lista-manga-hentai?orderby=views&page=$page", headers)
    override fun popularMangaSelector() = "div.col-md-3 a"
    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.setUrlWithoutDomain(element.attr("href"))
        manga.title = element.select("h5").text()
        manga.thumbnail_url = element.select("img").attr("abs:data-src")

        return manga
    }

    override fun popularMangaNextPageSelector() = "a[rel=next]"
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/lista-manga-hentai?orderby=last&page=$page", headers)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
        if (query.isNotBlank()) {
            url.addPathSegment("search")
            url.addQueryParameter("query", query) // query returns results all on one page
        } else {
            filters.forEach { filter ->
                when (filter) {
                    is GenreSelectFilter -> {
                        if (filter.state != 0) {
                            url.addPathSegments("lista-manga-hentai/category/${filter.toUriPart()}")
                            url.addQueryParameter("page", page.toString())
                        }
                    }
                    else -> {}
                }
            }
        }
        return GET(url.build().toString(), headers)
    }

    override fun searchMangaSelector() = "div.c-tabs-item__content > div.c-tabs-item__content, ${popularMangaSelector()}"
    override fun searchMangaFromElement(element: Element): SManga {
        return if (element.hasAttr("href")) {
            popularMangaFromElement(element) // genre search results
        } else {
            super.searchMangaFromElement(element) // query search results
        }
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    override fun chapterListSelector() = "ul.main.version-chap > li.wp-manga-chapter:not(:last-child)" // removing empty li
    override val pageListParseSelector = "div#all > img.img-responsive"

    override fun getFilterList() = FilterList(
        Filter.Header("Solo funciona si la consulta está en blanco"),
        GenreSelectFilter(),
    )

    class GenreSelectFilter : UriPartFilter(
        "Búsqueda de género",
        arrayOf(
            Pair("<seleccionar>", ""),
            Pair("Ecchi", "ecchi"),
            Pair("Yaoi", "yaoi"),
            Pair("Yuri", "yuri"),
            Pair("Anal", "anal"),
            Pair("Tetonas", "tetonas"),
            Pair("Escolares", "escolares"),
            Pair("Incesto", "incesto"),
            Pair("Virgenes", "virgenes"),
            Pair("Masturbacion", "masturbacion"),
            Pair("Maduras", "maduras"),
            Pair("Lolicon", "lolicon"),
            Pair("Bikini", "bikini"),
            Pair("Sirvientas", "sirvientas"),
            Pair("Enfermera", "enfermera"),
            Pair("Embarazada", "embarazada"),
            Pair("Ahegao", "ahegao"),
            Pair("Casadas", "casadas"),
            Pair("Chica Con Pene", "chica-con-pene"),
            Pair("Juguetes Sexuales", "juguetes-sexuales"),
            Pair("Orgias", "orgias"),
            Pair("Harem", "harem"),
            Pair("Romance", "romance"),
            Pair("Profesores", "profesores"),
            Pair("Tentaculos", "tentaculos"),
            Pair("Mamadas", "mamadas"),
            Pair("Shota", "shota"),
            Pair("Interracial", "interracial"),
            Pair("Full Color", "full-colo"),
            Pair("Sin Censura", "sin-censura"),
            Pair("Futanari", "futanari"),
            Pair("Doble Penetracion", "doble-penetracion"),
            Pair("Cosplay", "cosplay"),
        ),
    )
}
