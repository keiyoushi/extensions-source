package eu.kanade.tachiyomi.extension.it.animegdrclub

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Element

@Source
abstract class AnimeGDRClub : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = mangasParse(client.get("$baseUrl/serie.php"), popularMangaSelector(), 1)

    override suspend fun getLatestUpdates(page: Int): MangasPage = mangasParse(client.get("$baseUrl/"), latestUpdatesSelector(), 2)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/".toHttpUrl().newBuilder()
        val requestUrl = if (query.isNotEmpty()) {
            url.addEncodedPathSegment("serie.php")
            "$url#$query"
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
            if (status.isNotEmpty()) "$baseUrl/serie.php#stati=$status" else url.toString()
        }
        return mangasParse(client.get(requestUrl), searchMangaSelector(), 3)
    }

    private fun mangasParse(response: Response, selector: String, num: Int): MangasPage {
        var sele = selector
        var nume = num
        val encFrags = response.request.url.encodedFragment.toString().split('-')
        val document = response.asJsoup()
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

    // Popular/search entries store the site's relative href without a leading slash
    override fun getMangaUrl(manga: SManga): String = "$baseUrl/${manga.url.removePrefix("/")}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val infoElement = document.select(".tabellaalta")
        val details = SManga.create().apply {
            url = manga.url
            title = manga.title
            status = when {
                infoElement.text().contains("In Corso") -> SManga.ONGOING
                infoElement.text().contains("Concluso") -> SManga.COMPLETED
                infoElement.text().contains("Interrotto") -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
            genre = infoElement.select("span.generi > a").joinToString(", ") {
                it.text()
            }
            description = document.selectFirst("span.trama")?.text()?.substringAfter("Trama: ")
        }

        val chapterList = document.select(chapterListSelector()).map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.attr("href").replace("reader", "readerr"))
                name = element.text()
                chapter_number = element.text().removePrefix("Capitolo ").trim().toFloatOrNull() ?: 0f
            }
        }.reversed()

        return SMangaUpdate(details, chapterList)
    }

    private fun chapterListSelector() = ".capitoli_cont > a"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()

        val nomemanga = document.selectFirst("#nomemanga")?.attr("class")
        val numcap = document.selectFirst(".numcap")?.text()
        val maxpag = document.selectFirst(".maxpag")?.text()?.toIntOrNull()

        if (nomemanga != null && numcap != null && maxpag != null && maxpag > 0) {
            return (1..maxpag).map { page ->
                Page(page - 1, imageUrl = "$baseUrl/$nomemanga/cap.$numcap/$page.jpg")
            }
        }

        return document.select("img.corrente").mapIndexed { i, it ->
            Page(i, imageUrl = it.absUrl("src"))
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        eu.kanade.tachiyomi.source.model.Filter.Header("La ricerca non accetta i filtri e viceversa"),
        SelezType(listOf("Stato", "Genere")),
        StatusList(getStatusList()),
        GenreSelez(getGenreList()),
    )
}
