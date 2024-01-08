package eu.kanade.tachiyomi.extension.es.ikuhentai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Ikuhentai : ParsedHttpSource() {
    override val name = "Ikuhentai"
    override val baseUrl = "https://ikuhentai.net/"
    override val lang = "es"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/page/$page?s&post_type=wp-manga&m_orderby=views", headers)
    }
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/page/$page?s&post_type=wp-manga&m_orderby=latest", headers)
    }

    //    LIST SELECTOR
    override fun popularMangaSelector() = "div.c-tabs-item__content"
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun searchMangaSelector() = popularMangaSelector()

    //    ELEMENT
    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)

    //    NEXT SELECTOR
    override fun popularMangaNextPageSelector() = "a.nextpostslink"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("img").attr("data-src")
        element.select("div.tab-thumb > a").first()!!.let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.attr("title")
        }
        return manga
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/page/$page".toHttpUrlOrNull()!!.newBuilder()
        url.addQueryParameter("post_type", "wp-manga")
        val pattern = "\\s+".toRegex()
        val q = query.replace(pattern, "+")
        if (query.isNotEmpty()) {
            url.addQueryParameter("s", q)
        } else {
            url.addQueryParameter("s", "")
        }

        var orderBy: String

        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
//                is Status -> url.addQueryParameter("manga_status", arrayOf("", "completed", "ongoing")[filter.state])
                is GenreList -> {
                    val genreInclude = mutableListOf<String>()
                    filter.state.forEach {
                        if (it.state == 1) {
                            genreInclude.add(it.id)
                        }
                    }
                    if (genreInclude.isNotEmpty()) {
                        genreInclude.forEach { genre ->
                            url.addQueryParameter("genre[]", genre)
                        }
                    }
                }
                is StatusList -> {
                    val statuses = mutableListOf<String>()
                    filter.state.forEach {
                        if (it.state == 1) {
                            statuses.add(it.id)
                        }
                    }
                    if (statuses.isNotEmpty()) {
                        statuses.forEach { status ->
                            url.addQueryParameter("status[]", status)
                        }
                    }
                }

                is SortBy -> {
                    orderBy = filter.toUriPart()
                    url.addQueryParameter("m_orderby", orderBy)
                }
                is TextField -> url.addQueryParameter(filter.key, filter.state)
                else -> {}
            }
        }

        return GET(url.toString(), headers)
    }

    // max 200 results

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.site-content").first()!!

        val manga = SManga.create()
        manga.author = infoElement.select("div.author-content").text()
        manga.artist = infoElement.select("div.artist-content").text()

        val genres = mutableListOf<String>()
        infoElement.select("div.genres-content a").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }
        manga.genre = genres.joinToString(", ")
        manga.status = parseStatus(infoElement.select("div.post-status > div:nth-child(2) > div.summary-content").text())

        manga.description = document.select("div.description-summary").text()
        manga.thumbnail_url = document.select("div.summary_image > a > img").attr("data-src")

        return manga
    }

    private fun parseStatus(element: String): Int = when {
        element.lowercase().contains("ongoing") -> SManga.ONGOING
        element.lowercase().contains("completado") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "li.wp-manga-chapter"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()!!
        var url = urlElement.attr("href")
        url = url.replace("/p/1", "")
        url += "?style=list"
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(url)
        chapter.name = urlElement.text()

        return chapter
    }

    override fun prepareNewChapter(chapter: SChapter, manga: SManga) {
        val basic = Regex("""Chapter\s([0-9]+)""")
        when {
            basic.containsMatchIn(chapter.name) -> {
                basic.find(chapter.name)?.let {
                    chapter.chapter_number = it.groups[1]?.value!!.toFloat()
                }
            }
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        var i = 0
        document.select("div.reading-content * img").forEach { element ->
            val url = element.attr("data-src")
            i++
            if (url.isNotEmpty()) {
                pages.add(Page(i, "", url))
            }
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val imgHeader = Headers.Builder().apply {
            add("User-Agent", "Mozilla/5.0 (Linux; U; Android 4.1.1; en-gb; Build/KLP) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Safari/534.30")
            add("Referer", baseUrl)
        }.build()
        return GET(page.imageUrl!!, imgHeader)
    }

    //    private class Status : Filter.TriState("Completed")
    private class TextField(name: String, val key: String) : Filter.Text(name)
    private class SortBy : UriPartFilter(
        "Ordenar por",
        arrayOf(
            Pair("Relevance", ""),
            Pair("Latest", "latest"),
            Pair("A-Z", "alphabet"),
            Pair("Calificación", "rating"),
            Pair("Tendencia", "trending"),
            Pair("Más visto", "views"),
            Pair("Nuevo", "new-manga"),
        ),
    )

    private class Genre(name: String, val id: String = name) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)
    private class Status(name: String, val id: String = name) : Filter.TriState(name)
    private class StatusList(statuses: List<Status>) : Filter.Group<Status>("Estado", statuses)

    override fun getFilterList() = FilterList(
//            TextField("Judul", "title"),
        TextField("Autor", "author"),
        TextField("Año de publicación", "release"),
        SortBy(),
        StatusList(getStatusList()),
        GenreList(getGenreList()),
    )
    private fun getStatusList() = listOf(
        Status("Completado", "end"),
        Status("En emisión", "on-going"),
        Status("Cancelado", "canceled"),
        Status("Pausado", "on-hold"),
    )
    private fun getGenreList() = listOf(
        Genre("Ahegao", "ahegao"),
        Genre("Anal", "anal"),
        Genre("Bestiality", "bestialidad"),
        Genre("Bondage", "bondage"),
        Genre("Bukkake", "bukkake"),
        Genre("Chicas monstruo", "chicas-monstruo"),
        Genre("Chikan", "chikan"),
        Genre("Colegialas", "colegialas"),
        Genre("Comics porno", "comics-porno"),
        Genre("Dark Skin", "dark-skin"),
        Genre("Demonios", "demonios"),
        Genre("Ecchi", "ecchi"),
        Genre("Embarazadas", "embarazadas"),
        Genre("Enfermeras", "enfermeras"),
        Genre("Eroges", "eroges"),
        Genre("Fantasía", "fantasia"),
        Genre("Futanari", "futanari"),
        Genre("Gangbang", "gangbang"),
        Genre("Gemelas", "gemelas"),
        Genre("Gender Bender", "gender-bender"),
        Genre("Gore", "gore"),
        Genre("Handjob", "handjob"),
        Genre("Harem", "harem"),
        Genre("Hipnosis", "hipnosis"),
        Genre("Incesto", "incesto"),
        Genre("Loli", "loli"),
        Genre("Maids", "maids"),
        Genre("Masturbación", "masturbacion"),
        Genre("Milf", "milf"),
        Genre("Mind Break", "mind-break"),
        Genre("My Hero Academia", "my-hero-academia"),
        Genre("Naruto", "naruto"),
        Genre("Netorare", "netorare"),
        Genre("Paizuri", "paizuri"),
        Genre("Pokemon", "pokemon"),
        Genre("Profesora", "profesora"),
        Genre("Prostitución", "prostitucion"),
        Genre("Romance", "romance"),
        Genre("Straight Shota", "straight-shota"),
        Genre("Tentáculos", "tentaculos"),
        Genre("Virgen", "virgen"),
        Genre("Yaoi", "yaoi"),
        Genre("Yuri", "yuri"),
    )
    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
