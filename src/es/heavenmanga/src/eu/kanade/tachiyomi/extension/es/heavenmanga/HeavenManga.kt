package eu.kanade.tachiyomi.extension.es.heavenmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class HeavenManga : ParsedHttpSource() {

    override val name = "HeavenManga"

    override val baseUrl = "https://heavenmanga.com"

    override val lang = "es"

    // latest is broken on the site, it's the same as popular so turning it off
    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder {
        return Headers.Builder()
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) Gecko/20100101 Firefox/75")
    }

    override fun popularMangaSelector() = "div.page-item-detail"

    override fun latestUpdatesSelector() = "#container .ultimos_epis .not"

    override fun searchMangaSelector() = "div.c-tabs-item__content, ${popularMangaSelector()}"

    override fun chapterListSelector() = "div.listing-chapters_wrap tr"

    override fun popularMangaNextPageSelector() = "ul.pagination a[rel=next]"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/top?orderby=views&page=$page", headers)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val searchUrl = "$baseUrl/buscar?query=$query"

        // Filter
        val pageParameter = if (page > 1) "?page=$page" else ""

        if (query.isBlank()) {
            val ext = ".html"
            var name: String
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        if (filter.toUriPart().isNotBlank() && filter.state != 0) {
                            name = filter.toUriPart()
                            return GET("$baseUrl/genero/$name$ext$pageParameter", headers)
                        }
                    }
                    is AlphabeticoFilter -> {
                        if (filter.toUriPart().isNotBlank() && filter.state != 0) {
                            name = filter.toUriPart()
                            return GET("$baseUrl/letra/$name$ext$pageParameter", headers)
                        }
                    }
                    is ListaCompletasFilter -> {
                        if (filter.toUriPart().isNotBlank() && filter.state != 0) {
                            name = filter.toUriPart()
                            return GET("$baseUrl/$name$pageParameter", headers)
                        }
                    }
                    else -> {}
                }
            }
        }

        return GET(searchUrl, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        return if (response.request.url.toString().contains("query=")) {
            super.searchMangaParse(response)
        } else {
            popularMangaParse(response)
        }
    }

    // get contents of a url
    private fun getUrlContents(url: String): Document = client.newCall(GET(url, headers)).execute().asJsoup()

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("div.manga-name").text()
            setUrlWithoutDomain(element.select("a").attr("href"))
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        element.select("a").let {
            val latestChapter = getUrlContents(it.attr("href"))
            val url = latestChapter.select(".rpwe-clearfix:last-child a")
            setUrlWithoutDomain(url.attr("href"))
            title = it.select("span span").text()
            thumbnail_url = it.select("img").attr("src")
        }
    }

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        element.select("h4 a").let {
            title = it.text()
            setUrlWithoutDomain(it.attr("href"))
        }
        thumbnail_url = element.select("img").attr("abs:data-src")
    }

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            element.select("a").let {
                name = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            scanlator = element.select("span.pull-right").text()
        }
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        document.select("div.tab-summary").let { info ->
            genre = info.select("div.genres-content a").joinToString { it.text() }
            thumbnail_url = info.select("div.summary_image img").attr("abs:data-src")
        }
        description = document.select("div.description-summary p").text()
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    override fun pageListRequest(chapter: SChapter): Request {
        return getUrlContents(baseUrl + chapter.url).select("a[id=leer]").attr("abs:href")
            .let { GET(it, headers) }
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("script:containsData(pUrl)").first()!!.data()
            .substringAfter("pUrl=[").substringBefore("\"},];").split("\"},")
            .mapIndexed { i, string -> Page(i, "", string.substringAfterLast("\"")) }
    }

    /**
     * Array.from(document.querySelectorAll('.categorias a')).map(a => `Pair("${a.textContent}", "${a.getAttribute('href')}")`).join(',\n')
     * on https://heavenmanga.com/top/
     * */
    private class GenreFilter : UriPartFilter(
        "Géneros",
        arrayOf(
            Pair("Todo", ""),
            Pair("Accion", "accion"),
            Pair("Adulto", "adulto"),
            Pair("Aventura", "aventura"),
            Pair("Artes Marciales", "artes+marciales"),
            Pair("Acontesimientos de la Vida", "acontesimientos+de+la+vida"),
            Pair("Bakunyuu", "bakunyuu"),
            Pair("Sci-fi", "sci-fi"),
            Pair("Comic", "comic"),
            Pair("Combate", "combate"),
            Pair("Comedia", "comedia"),
            Pair("Cooking", "cooking"),
            Pair("Cotidiano", "cotidiano"),
            Pair("Colegialas", "colegialas"),
            Pair("Critica social", "critica+social"),
            Pair("Ciencia ficcion", "ciencia+ficcion"),
            Pair("Cambio de genero", "cambio+de+genero"),
            Pair("Cosas de la Vida", "cosas+de+la+vida"),
            Pair("Drama", "drama"),
            Pair("Deporte", "deporte"),
            Pair("Doujinshi", "doujinshi"),
            Pair("Delincuentes", "delincuentes"),
            Pair("Ecchi", "ecchi"),
            Pair("Escolar", "escolar"),
            Pair("Erotico", "erotico"),
            Pair("Escuela", "escuela"),
            Pair("Estilo de Vida", "estilo+de+vida"),
            Pair("Fantasia", "fantasia"),
            Pair("Fragmentos de la Vida", "fragmentos+de+la+vida"),
            Pair("Gore", "gore"),
            Pair("Gender Bender", "gender+bender"),
            Pair("Humor", "humor"),
            Pair("Harem", "harem"),
            Pair("Haren", "haren"),
            Pair("Hentai", "hentai"),
            Pair("Horror", "horror"),
            Pair("Historico", "historico"),
            Pair("Josei", "josei"),
            Pair("Loli", "loli"),
            Pair("Light", "light"),
            Pair("Lucha Libre", "lucha+libre"),
            Pair("Manga", "manga"),
            Pair("Mecha", "mecha"),
            Pair("Magia", "magia"),
            Pair("Maduro", "maduro"),
            Pair("Manhwa", "manhwa"),
            Pair("Manwha", "manwha"),
            Pair("Mature", "mature"),
            Pair("Misterio", "misterio"),
            Pair("Mutantes", "mutantes"),
            Pair("Novela", "novela"),
            Pair("Orgia", "orgia"),
            Pair("OneShot", "oneshot"),
            Pair("OneShots", "oneshots"),
            Pair("Psicologico", "psicologico"),
            Pair("Romance", "romance"),
            Pair("Recuentos de la vida", "recuentos+de+la+vida"),
            Pair("Smut", "smut"),
            Pair("Shojo", "shojo"),
            Pair("Shonen", "shonen"),
            Pair("Seinen", "seinen"),
            Pair("Shoujo", "shoujo"),
            Pair("Shounen", "shounen"),
            Pair("Suspenso", "suspenso"),
            Pair("School Life", "school+life"),
            Pair("Sobrenatural", "sobrenatural"),
            Pair("SuperHeroes", "superheroes"),
            Pair("Supernatural", "supernatural"),
            Pair("Slice of Life", "slice+of+life"),
            Pair("Super Poderes", "ssuper+poderes"),
            Pair("Terror", "terror"),
            Pair("Torneo", "torneo"),
            Pair("Tragedia", "tragedia"),
            Pair("Transexual", "transexual"),
            Pair("Vida", "vida"),
            Pair("Vampiros", "vampiros"),
            Pair("Violencia", "violencia"),
            Pair("Vida Pasada", "vida+pasada"),
            Pair("Vida Cotidiana", "vida+cotidiana"),
            Pair("Vida de Escuela", "vida+de+escuela"),
            Pair("Webtoon", "webtoon"),
            Pair("Webtoons", "webtoons"),
            Pair("Yaoi", "yaoi"),
            Pair("Yuri", "yuri"),
        ),
    )

    /**
     * Array.from(document.querySelectorAll('.letras a')).map(a => `Pair("${a.textContent}", "${a.getAttribute('href')}")`).join(',\n')
     * on https://heavenmanga.com/top/
     * */
    private class AlphabeticoFilter : UriPartFilter(
        "Alfabético",
        arrayOf(
            Pair("Todo", ""),
            Pair("A", "a"),
            Pair("B", "b"),
            Pair("C", "c"),
            Pair("D", "d"),
            Pair("E", "e"),
            Pair("F", "f"),
            Pair("G", "g"),
            Pair("H", "h"),
            Pair("I", "i"),
            Pair("J", "j"),
            Pair("K", "k"),
            Pair("L", "l"),
            Pair("M", "m"),
            Pair("N", "n"),
            Pair("O", "o"),
            Pair("P", "p"),
            Pair("Q", "q"),
            Pair("R", "r"),
            Pair("S", "s"),
            Pair("T", "t"),
            Pair("U", "u"),
            Pair("V", "v"),
            Pair("W", "w"),
            Pair("X", "x"),
            Pair("Y", "y"),
            Pair("Z", "z"),
            Pair("0-9", "0-9"),
        ),
    )

    /**
     * Array.from(document.querySelectorAll('#t li a')).map(a => `Pair("${a.textContent}", "${a.getAttribute('href')}")`).join(',\n')
     * on https://heavenmanga.com/top/
     * */
    private class ListaCompletasFilter : UriPartFilter(
        "Lista Completa",
        arrayOf(
            Pair("Todo", ""),
            Pair("Lista Comis", "comic"),
            Pair("Lista Novelas", "novela"),
            Pair("Lista Adulto", "adulto"),
        ),
    )

    override fun getFilterList() = FilterList(
        // Search and filter don't work at the same time
        Filter.Header("NOTA: Los filtros se ignoran si se utiliza la búsqueda de texto."),
        Filter.Header("Sólo se puede utilizar un filtro a la vez."),
        Filter.Separator(),
        GenreFilter(),
        AlphabeticoFilter(),
        ListaCompletasFilter(),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
