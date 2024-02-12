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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class HeavenManga : ParsedHttpSource() {

    override val name = "HeavenManga"

    override val baseUrl = "https://heavenmanga.com"

    override val lang = "es"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/top?orderby=views&page=$page", headers)

    override fun popularMangaSelector() = "div.page-item-detail"

    override fun popularMangaNextPageSelector() = "ul.pagination a[rel=next]"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("div.manga-name").text()
            setUrlWithoutDomain(element.select("a").attr("href"))
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun latestUpdatesRequest(page: Int) = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.selectFirst(latestUpdatesWrapperSelector())!!
            .select(latestUpdatesSelector())
            .map { element ->
                latestUpdatesFromElement(element)
            }.distinctBy { it.url }

        val hasNextPage = latestUpdatesNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun latestUpdatesWrapperSelector() = ".container #loop-content "

    override fun latestUpdatesSelector() = "span.list-group-item:not(:has(> div.row:containsOwn(Novela)))"

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        with(element.selectFirst("a")!!) {
            val mangaUrl = attr("href").substringBeforeLast("/")
            setUrlWithoutDomain(mangaUrl)
            title = select(".captitle").text()
            thumbnail_url = mangaUrl.replace("/manga/", "/uploads/manga/") + "/cover/cover_250x350.jpg"
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
        if (query.isNotBlank()) {
            if (query.length < 3) throw Exception("La búsqueda debe tener al menos 3 caracteres")
            url.addPathSegment("buscar")
                .addQueryParameter("query", query)
        } else {
            val ext = ".html"
            var name: String
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        if (filter.toUriPart().isNotBlank() && filter.state != 0) {
                            name = filter.toUriPart()
                            url.addPathSegment("genero")
                                .addPathSegment(name + ext)
                        }
                    }
                    is AlphabeticoFilter -> {
                        if (filter.toUriPart().isNotBlank() && filter.state != 0) {
                            name = filter.toUriPart()
                            url.addPathSegment("letra")
                                .addPathSegment("manga$ext")
                                .addQueryParameter("alpha", name)
                        }
                    }
                    is ListaCompletasFilter -> {
                        if (filter.toUriPart().isNotBlank() && filter.state != 0) {
                            name = filter.toUriPart()
                            url.addPathSegment(name)
                        }
                    }
                    else -> {}
                }
            }
        }

        if (page > 1) url.addQueryParameter("page", page.toString())

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        return if (response.request.url.pathSegments.contains("buscar")) {
            super.searchMangaParse(response)
        } else {
            popularMangaParse(response)
        }
    }

    override fun searchMangaSelector() = "div.c-tabs-item__content"

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        element.select("h4 a").let {
            title = it.text()
            setUrlWithoutDomain(it.attr("href"))
        }
        thumbnail_url = element.select("img").attr("abs:data-src")
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        document.select("div.tab-summary").let { info ->
            genre = info.select("div.genres-content a").joinToString { it.text() }
            thumbnail_url = info.select("div.summary_image img").attr("abs:data-src")
        }
        description = document.select("div.description-summary p").text()
    }

    override fun chapterListRequest(manga: SManga): Request {
        val mangaUrl = (baseUrl + manga.url).toHttpUrl().newBuilder()
            .addQueryParameter("columns[0][data]", "number")
            .addQueryParameter("columns[0][orderable]", "true")
            .addQueryParameter("columns[1][data]", "created_at")
            .addQueryParameter("columns[1][searchable]", "true")
            .addQueryParameter("order[0][column]", "1")
            .addQueryParameter("order[0][dir]", "desc")
            .addQueryParameter("start", "0")
            .addQueryParameter("length", CHAPTER_LIST_LIMIT.toString())

        val headers = headersBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        return GET(mangaUrl.build(), headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaUrl = response.request.url.toString().substringBefore("?").removeSuffix("/")
        val result = json.decodeFromString<PayloadChaptersDto>(response.body.string())
        return result.data.map {
            SChapter.create().apply {
                name = "Capítulo: ${it.slug}"
                setUrlWithoutDomain("$mangaUrl/${it.slug}#${it.id}")
                date_upload = runCatching { dateFormat.parse(it.createdAt)?.time ?: 0 }.getOrDefault(0)
            }
        }
    }

    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("#")
        if (chapterId.isBlank()) throw Exception("Error al obtener el id del capítulo. Actualice la lista")
        val url = "$baseUrl/manga/leer/$chapterId"
        return GET(url, headers)
    }

    override fun pageListParse(document: Document): List<Page> {
        val data = document.select("script:containsData(pUrl)").first()!!.data()
        val jsonString = PAGES_REGEX.find(data)!!.groupValues[1].removeTrailingComma()
        val pages = json.decodeFromString<List<PageDto>>(jsonString)
        return pages.mapIndexed { i, dto -> Page(i, "", dto.imgURL) }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    private class GenreFilter : UriPartFilter(
        "Géneros",
        arrayOf(
            Pair("<Seleccionar>", ""),
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
            Pair("<Seleccionar>", ""),
            Pair("Other", "Other"),
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
            Pair("<Seleccionar>", ""),
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

    private fun String.removeTrailingComma() = replace(TRAILING_COMMA_REGEX, "$1")

    companion object {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val PAGES_REGEX = """pUrl\s*=\s*(\[.*\])\s*;""".toRegex()
        val TRAILING_COMMA_REGEX = """,\s*(\}|\])""".toRegex()
        private const val CHAPTER_LIST_LIMIT = 10000
    }
}
