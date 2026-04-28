package eu.kanade.tachiyomi.extension.it.animegdrclub

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

class AnimeGDRClub : HttpSource() {
    override val name = "Anime GDR Club"
    override val baseUrl = "http://www.agcscanlation.it/"
    override val lang = "it"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/serie.php", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/".toHttpUrl().newBuilder()
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

    private fun popularMangaSelector() = "div.manga"
    private fun latestUpdatesSelector() = ".containernews > a"
    private fun searchMangaSelector() = ".listonegen > a"

    private fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = "$baseUrl/${element.selectFirst("img")!!.attr("src")}"
        manga.url = element.selectFirst("a.linkalmanga")!!.attr("href")
        manga.title = element.selectFirst("div.nomeserie > span")!!.text()
        return manga
    }

    private fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain("$baseUrl/progetto.php?nome=${element.attr("href").toHttpUrl().queryParameter("nome")}")
        manga.title = element.selectFirst(".titolo")!!.text()
        manga.thumbnail_url = "$baseUrl/${element.selectFirst("img")!!.attr("src")}"
        return manga
    }

    private fun searchMangaFromElement(element: Element): SManga = latestUpdatesFromElement(element)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
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
        manga.initialized = true
        return manga
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()
        document.select(chapterListSelector()).forEach { element ->
            chapters.add(
                SChapter.create().apply {
                    setUrlWithoutDomain(element.attr("href").replace("reader", "readerr"))
                    name = element.text()
                    chapter_number = element.text().removePrefix("Capitolo ").trim().toFloatOrNull() ?: 0f
                },
            )
        }
        chapters.reverse()
        return chapters
    }

    private fun chapterListSelector() = ".capitoli_cont > a"

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val nomemanga = document.selectFirst("#nomemanga")?.attr("class")
        val numcap = document.selectFirst(".numcap")?.text()
        val maxpag = document.selectFirst(".maxpag")?.text()?.toIntOrNull()

        if (nomemanga != null && numcap != null && maxpag != null && maxpag > 0) {
            return (1..maxpag).map { page ->
                Page(page - 1, imageUrl = "$baseUrl$nomemanga/cap.$numcap/$page.jpg")
            }
        }

        return document.select("img.corrente").mapIndexed { i, it ->
            Page(i, imageUrl = baseUrl + it.attr("src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imgHeader = Headers.Builder().apply {
            add("Referer", baseUrl)
        }.build()
        return GET(page.imageUrl!!, imgHeader)
    }

    override fun getFilterList() = FilterList(
        eu.kanade.tachiyomi.source.model.Filter.Header("La ricerca non accetta i filtri e viceversa"),
        SelezType(listOf("Stato", "Genere")),
        StatusList(getStatusList()),
        GenreSelez(getGenreList()),
    )
}
