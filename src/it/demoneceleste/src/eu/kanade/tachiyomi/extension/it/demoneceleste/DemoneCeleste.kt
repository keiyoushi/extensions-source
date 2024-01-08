package eu.kanade.tachiyomi.extension.it.demoneceleste

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class DemoneCeleste : ParsedHttpSource() {

    override val name = "DemoneCeleste"
    override val baseUrl = "https://www.demoneceleste.it/"
    override val lang = "it"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    private val bgImgUrlRegex = """\((.*)\)""".toRegex()

    //region REQUESTS

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/", headers)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search?sez=serie".toHttpUrlOrNull()!!.newBuilder()

        if (query.isNotEmpty()) {
            url.addQueryParameter("key", query)
        } else {
            url.addQueryParameter("key", "")
        }

        var status = ""

        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is GenreList -> {
                    val genreInclude = mutableListOf<String>()
                    filter.state.forEach {
                        if (it.state) {
                            genreInclude.add(it.id)
                        }
                    }
                    if (genreInclude.isNotEmpty()) {
                        genreInclude.forEach { genre ->
                            url.addQueryParameter("tag[]", genre)
                        }
                    }
                }
                is StatusList -> {
                    var i = 0
                    filter.state.forEach {
                        if (it.state) {
                            status += "${if (i != 0) "-" else ""}${it.id}"
                            i++
                        }
                    }
                }
                else -> {}
            }
        }
        return GET("$url#$status", headers)
    }
    //endregion

    //region CONTENTS INFO
    private fun mangasParse(response: Response, selector: String, num: Int): MangasPage {
        val document = response.asJsoup()
        var sele = selector

        val encFrags = response.request.url.encodedFragment.toString().split('-')

        if ((encFrags[0].isNotEmpty()) and (encFrags[0] != "null")) sele += ":matches(${encFrags.joinToString("|")})"

        val mangas = document.select(sele).map { element ->
            when (num) {
                1 -> popularMangaFromElement(element)
                2 -> latestUpdatesFromElement(element)
                else -> searchMangaFromElement(element)
            }
        }
        return MangasPage(mangas, false)
    }
    override fun popularMangaParse(response: Response): MangasPage = mangasParse(response, popularMangaSelector(), 1)
    override fun latestUpdatesParse(response: Response): MangasPage = mangasParse(response, latestUpdatesSelector(), 2)
    override fun searchMangaParse(response: Response): MangasPage = mangasParse(response, searchMangaSelector(), 3)

    override fun popularMangaSelector() = ".col-md-6.row.no-pad:has(a.manga[href^=manga])"
    override fun latestUpdatesSelector() = ".col.bg-light.ombra:has(a[href^=manga])"
    override fun searchMangaSelector() = "div#serie .col-md-10.row.no-pad:has(h4 a[href^=manga])"

    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.thumbnail_url = "$baseUrl/${(bgImgUrlRegex.find(element.select("a > div > div").first()!!.attr("style")))!!.groupValues[1]}".replace("pub", "det")
        element.select("a").first()!!.let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }

        return manga
    }
    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.thumbnail_url = "$baseUrl/${(bgImgUrlRegex.find(element.select(".col-md-auto.no-pad > a > div").first()!!.attr("style")))!!.groupValues[1]}".replace("pub", "det")
        element.select("a.manga[href^=manga]").first()!!.let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select(".text-md-left.col-md-12.col-6.p-0")
        val manga = SManga.create()

        manga.status = when {
            infoElement.text().lowercase().contains("in corso") -> SManga.ONGOING
            infoElement.text().lowercase().contains("concluso") -> SManga.COMPLETED
            infoElement.text().lowercase().contains("sospeso") -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        manga.author = infoElement.select("p:has(strong:contains(Autore))").text().replace("Autore: ", "")
        manga.artist = infoElement.select("p:has(strong:contains(Artista))").text().replace("Artista: ", "")
        manga.genre = infoElement.select("p:has(strong:contains(Tag))").text().replace("Tag: ", "")
        manga.description = document.select(".text-justify").text().let {
            if (it.isNullOrEmpty()) "Questo manga non è ancora stato pubblicato dagli scanner. Controlla tra un po'. Per cercare aggiornamenti riavvia la pagina." else it
        }

        return manga
    }
    //endregion

    //region NEXT SELECTOR  -  Not used

    override fun popularMangaNextPageSelector(): String? = null
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    //endregion

    //region CHAPTER in CONTENTS INFO

    override fun chapterListSelector() = "a[href^=\"manga/\"]:has(strong)"
    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        if (element.text().contains("Oneshot")) {
            chapter.setUrlWithoutDomain(element.attr("href") + "#0")
            chapter.name = element.text()
        } else {
            val container = element.parent()!!.parent()!!

            chapter.setUrlWithoutDomain("${element.attr("href")}#${element.parent()!!.select("small").first()!!.text().filter { it.isDigit() }.toInt()}")
            chapter.name = container.parent()!!.previousElementSibling()!!.text().replace("""Capp.*""".toRegex(), " Ch.").replace("Volume", "Vol.") + element.text().replace(" #", " - ")
            chapter.date_upload = SimpleDateFormat("dd-MM-yyyy", Locale.ITALY).parse(container.select("small").last()!!.text().replace('/', '-'))!!.time
        }

        return chapter
    }
    override fun prepareNewChapter(chapter: SChapter, manga: SManga) {
        val basic = Regex("""Ch\.([0-9]+)""")
        when {
            basic.containsMatchIn(chapter.name) -> {
                basic.find(chapter.name)?.let {
                    chapter.chapter_number = it.groups[1]?.value!!.toFloat()
                }
            }
        }
    }
    //endregion

    //region PAGE loading
    override fun pageListRequest(chapter: SChapter): Request {
        val (id, n) = chapter.url.replace("manga/", "").replace("""/#([0-9]+)""".toRegex(), "").split("/")
        return POST(
            "$baseUrl/ajax.php",
            headers,
            FormBody.Builder().apply {
                add("ajax", "pagine")
                add("id", id)
                add("n", n)
                add("leggo", "1")
            }.build(),
        )
    }
    override fun pageListParse(response: Response): List<Page> {
        val body = response.body

        val risultati = body.string().replace("<pagine><pag>", "").replace("""</pag><linkforum>.*""".toRegex(), "").split("</pag><pag>")
        // The line above may be changed with this : - Not used because I couldn't find a way to use Regex's Global flag in Kotlin
        // val results = """<pag>(.*?)</pag>""".toRegex().find(body.string())!!.groups
        val pages = mutableListOf<Page>()

        if (risultati.toString().contains("Accedi al sito")) {
            pages.add(Page(1, "", "https://i.imgur.com/fiqTAUt.png"))
            return pages
        }

        risultati.forEach {
            pages.add(Page(risultati.indexOf(it), "", baseUrl + it))
        }

        return pages
    }
    override fun pageListParse(document: Document): List<Page> {
        throw UnsupportedOperationException("Not used.")
    }
    //endregion

    override fun imageUrlParse(document: Document) = ""
    override fun imageRequest(page: Page): Request {
        val imgHeader = Headers.Builder().apply {
            add("User-Agent", "Mozilla/5.0 (Linux; U; Android 4.1.1; en-gb; Build/KLP) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Safari/534.30")
            add("Referer", baseUrl)
        }.build()
        return GET(page.imageUrl!!, imgHeader)
    }

    //region FILTERS
    private class Genre(name: String, val id: String = name) : Filter.CheckBox(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Generi", genres)
    private class Status(name: String, val id: String = name) : Filter.CheckBox(name, true)
    private class StatusList(statuses: List<Status>) : Filter.Group<Status>("Stato", statuses)

    override fun getFilterList() = FilterList(
        StatusList(getStatusList()),
        GenreList(getGenreList()),
    )

    private fun getStatusList() = listOf(
        Status("In corso", "in corso"),
        Status("Finito", "concluso-Oneshot"),
        Status("Sospeso", "sospeso"),
    )
    private fun getGenreList() = listOf(
        Genre("Avventura", "Avventura"),
        Genre("Azione", "Azione"),
        Genre("Color", "Color"),
        Genre("Commedia", "Commedia"),
        Genre("Crossdressing", "Crossdressing"),
        Genre("Drammatico", "Drammatico"),
        Genre("Ecchi", "Ecchi"),
        Genre("Fantasy", "Fantasy"),
        Genre("Harem", "Harem"),
        Genre("Horror", "Horror"),
        Genre("Isekai", "Isekai"),
        Genre("Josei", "Josei"),
        Genre("LongStrip", "LongStrip"),
        Genre("Magia", "Magia"),
        Genre("Manhua", "Manhua"),
        Genre("Maturo", "Maturo"),
        Genre("Mistero", "Mistero"),
        Genre("Musica", "Musica"),
        Genre("OneShot", "OneShot"),
        Genre("Poliziesco", "Poliziesco"),
        Genre("Psicologico", "Psicologico"),
        Genre("Romantico", "Romantico"),
        Genre("Sci-fi", "Sci-fi"),
        Genre("Scolastico", "Scolastico"),
        Genre("Seinen", "Seinen"),
        Genre("Shonen", "Shonen"),
        Genre("Shoujo", "Shoujo"),
        Genre("Slice_of_Life", "Slice_of_Life"),
        Genre("Soprannaturale", "Soprannaturale"),
        Genre("Spokon", "Spokon"),
        Genre("Sport", "Sport"),
        Genre("Storico", "Storico"),
    )
    //endregion
}
