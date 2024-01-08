package eu.kanade.tachiyomi.extension.it.animegdrclub

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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class AnimeGDRClub : ParsedHttpSource() {
    override val name = "Anime GDR Club"
    override val baseUrl = "http://www.agcscanlation.it/"
    override val lang = "it"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    //region REQUESTS

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/serie.php", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/", headers)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/".toHttpUrlOrNull()!!.newBuilder()

        if (query.isNotEmpty()) {
            url.addEncodedPathSegment("serie.php")
            return GET("$url#$query", headers)
        } else {
            url.addEncodedPathSegment("listone.php")

            var status = ""
            var filtertype = ""

            (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
                when (filter) {
                    is SelezType -> {
                        filtertype = filter.values[filter.state]
                    }
                    is GenreSelez -> {
                        if (filtertype == "Genere") {
                            url.addQueryParameter("genere", filter.values[filter.state])
                        }
                    }
                    is StatusList -> {
                        if (filtertype == "Stato") {
                            var i = 0
                            filter.state.forEach {
                                if (it.state) {
                                    status += "${if (i != 0) "-" else ""}${it.id}"
                                    i++
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }

            return GET(if (status.isNotEmpty()) "$baseUrl/serie.php#stati=$status" else url.toString(), headers)
        }
    }
    //endregion

    //region CONTENTS INFO
    private fun mangasParse(response: Response, selector: String, num: Int): MangasPage {
        val document = response.asJsoup()
        var sele = selector
        var nume = num

        val encFrags = response.request.url.encodedFragment.toString().split('-')

        if ((encFrags[0].isNotEmpty()) and (encFrags[0] != "null")) {
            nume = 1
            sele = if (encFrags[0].startsWith("stati=")) {
                encFrags.joinToString(", ") {
                    ".${it.replace("stati=", "")} > .manga"
                }
            } else {
                "div.manga:contains(${encFrags.joinToString("-")})"
            }
        }

        val mangas = document.select(sele).map { element ->
            when (nume) {
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

    override fun popularMangaSelector() = "div.manga"
    override fun latestUpdatesSelector() = ".containernews > a"
    override fun searchMangaSelector() = ".listonegen > a"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.thumbnail_url = "$baseUrl/${element.selectFirst("img")!!.attr("src")}"
        manga.url = element.selectFirst("a.linkalmanga")!!.attr("href")
        manga.title = element.selectFirst("div.nomeserie > span")!!.text()

        return manga
    }
    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.setUrlWithoutDomain("http://www.agcscanlation.it/progetto.php?nome=${element.attr("href").toHttpUrlOrNull()!!.queryParameter("nome")}")
        manga.title = element.selectFirst(".titolo")!!.text()
        manga.thumbnail_url = "$baseUrl/${element.selectFirst("img")!!.attr("src")}"

        return manga
    }
    override fun searchMangaFromElement(element: Element): SManga = latestUpdatesFromElement(element)

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select(".tabellaalta")
        val manga = SManga.create()

        manga.status = when {
            infoElement.text().contains("In Corso") -> SManga.ONGOING
            infoElement.text().contains("Concluso") -> SManga.COMPLETED
            infoElement.text().contains("Interrotto") -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        manga.genre = infoElement.select("span.generi > a").joinToString(", ") {
            it.text()
        }
        manga.description = document.select("span.trama").text().substringAfter("Trama: ")

        return manga
    }
    //endregion

    //region NEXT SELECTOR  -  Not used

    override fun popularMangaNextPageSelector(): String? = null
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    //endregion

    //region CHAPTER in CONTENTS INFO

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()

        document.select(chapterListSelector()).forEach {
            chapters.add(
                SChapter.create().apply {
                    setUrlWithoutDomain(it.attr("href").replace("reader", "readerr"))
                    name = it.text()
                    chapter_number = it.text().filter { it.isDigit() }.toFloat()
                },
            )
        }

        chapters.reverse()
        return chapters
    }

    override fun chapterListSelector() = ".capitoli_cont > a"
    override fun chapterFromElement(element: Element) = throw Exception("Not Used")
    //endregion

    //region PAGE loading

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select("img.corrente").forEachIndexed { i, it ->
            pages.add(Page(i, "", baseUrl + it.attr("src")))
        }

        return pages
    }

    override fun imageUrlParse(document: Document) = ""
    override fun imageRequest(page: Page): Request {
        val imgHeader = Headers.Builder().apply {
            add("Referer", baseUrl)
        }.build()
        return GET(page.imageUrl!!, imgHeader)
    }
    //endregion

    //region FILTERS
    private class SelezType(options: List<String>) : Filter.Select<String>("Scegli quale usare", options.toTypedArray(), 1)
    private class GenreSelez(genres: List<String>) : Filter.Select<String>("Genere", genres.toTypedArray(), 0)
    private class Status(name: String, val id: String = name) : Filter.CheckBox(name, true)
    private class StatusList(statuses: List<Status>) : Filter.Group<Status>("Stato", statuses)

    override fun getFilterList() = FilterList(
        Filter.Header("La ricerca non accetta i filtri e viceversa"),
        SelezType(listOf("Stato", "Genere")),
        StatusList(getStatusList()),
        GenreSelez(getGenreList()),
    )

    private fun getStatusList() = listOf(
        Status("In corso", "progettiincorso"),
        Status("Finito", "progetticonclusi-progettioneshot"),
        Status("Interrotto", "progettiinterrotti"),
    )
    private fun getGenreList() = listOf("Avventura", "Azione", "Comico", "Commedia", "Drammatico", "Ecchi", "Fantascienza", "Fantasy", "Guerra", "Harem", "Horror", "Isekai", "Mecha", "Mistero", "Musica", "Psicologico", "Scolastico", "Sentimentale", "Slice of Life", "Sovrannaturale", "Sperimentale", "Storico", "Thriller")
    //endregion
}
