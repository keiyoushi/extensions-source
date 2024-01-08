package eu.kanade.tachiyomi.multisrc.mangaworld

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
import java.lang.Exception
import java.lang.UnsupportedOperationException
import java.text.SimpleDateFormat
import java.util.Locale

abstract class MangaWorld(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : ParsedHttpSource() {

    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    companion object {
        protected val CHAPTER_NUMBER_REGEX by lazy { Regex("""(?i)capitolo\s([0-9]+)""") }

        protected val DATE_FORMATTER by lazy { SimpleDateFormat("dd MMMM yyyy", Locale.ITALY) }
        protected val DATE_FORMATTER_2 by lazy { SimpleDateFormat("H", Locale.ITALY) }
    }

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/archive?sort=most_read&page=$page", headers)
    }
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/?page=$page", headers)
    }

    override fun searchMangaSelector() = "div.comics-grid .entry"
    override fun popularMangaSelector() = searchMangaSelector()
    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("a.thumb img").attr("src")
        element.select("a").first()!!.let {
            manga.setUrlWithoutDomain(it.attr("href").removeSuffix("/"))
            manga.title = it.attr("title")
        }
        return manga
    }
    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? = null
    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()
    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(latestUpdatesSelector()).map { element ->
            searchMangaFromElement(element)
        }
        // nextPage is not possible because pagination is loaded after via Javascript
        // 16 is the default manga-per-page. If it is less than 16 then there's no next page
        val hasNextPage = mangas.size == 16
        return MangasPage(mangas, hasNextPage)
    }
    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)
    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/archive?page=$page".toHttpUrlOrNull()!!.newBuilder()
        url.addQueryParameter("keyword", query)

        filters.forEach { filter ->
            when (filter) {
                is GenreList ->
                    filter.state.filter { it.state }.forEach {
                        url.addQueryParameter("genre", it.id)
                    }
                is StatusList ->
                    filter.state.filter { it.state }.forEach {
                        url.addQueryParameter("status", it.id)
                    }
                is MTypeList ->
                    filter.state.filter { it.state }.forEach {
                        url.addQueryParameter("type", it.id)
                    }
                is SortBy -> url.addQueryParameter("sort", filter.toUriPart())
                is TextField -> url.addQueryParameter(filter.key, filter.state)
                else -> {}
            }
        }
        return GET(url.toString(), headers)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.comic-info")
        if (infoElement.isEmpty()) {
            throw Exception("Page not found")
        }

        val manga = SManga.create()
        manga.author = infoElement.select("a[href*=/archive?author=]").first()?.text()
        manga.artist = infoElement.select("a[href*=/archive?artist=]").text()
        manga.thumbnail_url = infoElement.select(".thumb > img").attr("src")

        var description = document.select("div#noidungm").text()
        val otherTitle = document.select("div.meta-data > div").first()?.text()
        if (!otherTitle.isNullOrBlank() && otherTitle.contains("Titoli alternativi")) {
            description += "\n\n$otherTitle"
        }
        manga.description = description.trim()

        manga.genre = infoElement.select("div.meta-data a.badge").joinToString(", ") {
            it.text()
        }

        val status = infoElement.select("a[href*=/archive?status=]").first()?.text()
        manga.status = parseStatus(status)

        return manga
    }

    protected fun parseStatus(element: String?): Int {
        if (element.isNullOrEmpty()) {
            return SManga.UNKNOWN
        }
        return when (element.lowercase()) {
            "in corso" -> SManga.ONGOING
            "finito" -> SManga.COMPLETED
            "in pausa" -> SManga.ON_HIATUS
            "cancellato" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    override fun chapterListSelector() = ".chapters-wrapper .chapter"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        val url = element.select("a.chap").first()?.attr("href")
            ?: throw throw Exception("Url not found")
        chapter.setUrlWithoutDomain(fixChapterUrl(url))

        val name = element.select("span.d-inline-block").first()?.text() ?: ""
        chapter.name = name

        val date = parseChapterDate(element.select(".chap-date").last()?.text())
        chapter.date_upload = date

        val number = parseChapterNumber(name)
        if (number != null) {
            chapter.chapter_number = number
        }
        return chapter
    }

    protected fun fixChapterUrl(url: String?): String {
        if (url.isNullOrEmpty()) {
            return ""
        }
        val params = url.split("?").let { if (it.size > 1) it[1] else "" }
        return when {
            params.contains("style=list") -> url
            params.contains("style=pages") ->
                url.replace("style=pages", "style=list")
            params.isEmpty() -> "$url?style=list"
            else -> "$url&style=list"
        }
    }

    protected fun parseChapterDate(string: String?): Long {
        if (string == null) {
            return 0L
        }
        return runCatching { DATE_FORMATTER.parse(string)?.time }.getOrNull()
            ?: runCatching { DATE_FORMATTER_2.parse(string)?.time }.getOrNull() ?: 0L
    }

    protected fun parseChapterNumber(string: String): Float? {
        return CHAPTER_NUMBER_REGEX.find(string)?.let {
            it.groups[1]?.value?.toFloat()
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div#page img.page-image").mapIndexed { index, it ->
            val url = it.attr("src")
            Page(index, imageUrl = url)
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used.")

    override fun imageRequest(page: Page): Request {
        val imgHeader = Headers.Builder().apply {
            add("User-Agent", "Mozilla/5.0 (Linux; U; Android 4.1.1; en-gb; Build/KLP) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Safari/534.30")
            add("Referer", baseUrl)
        }.build()
        return GET(page.imageUrl!!, imgHeader)
    }

    override fun getFilterList() = FilterList(
        TextField("Anno di uscita", "year"),
        SortBy(),
        StatusList(getStatusList()),
        GenreList(getGenreList()),
        MTypeList(getTypesList()),
    )

    private class SortBy : UriPartFilter(
        "Ordina per",
        arrayOf(
            Pair("Rilevanza", ""),
            Pair("Più letti", "most_read"),
            Pair("Meno letti", "less_read"),
            Pair("Più recenti", "newest"),
            Pair("Meno recenti", "oldest"),
            Pair("A-Z", "a-z"),
            Pair("Z-A", "z-a"),
        ),
    )

    private class TextField(name: String, val key: String) : Filter.Text(name)

    class Genre(name: String, val id: String = name) : Filter.CheckBox(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Generi", genres)

    class MType(name: String, val id: String = name) : Filter.CheckBox(name)
    private class MTypeList(types: List<MType>) : Filter.Group<MType>("Tipologia", types)

    class Status(name: String, val id: String = name) : Filter.CheckBox(name)
    private class StatusList(statuses: List<Status>) : Filter.Group<Status>("Stato", statuses)

    protected fun getGenreList() = listOf(
        Genre("Adulti", "adulti"),
        Genre("Arti Marziali", "arti-marziali"),
        Genre("Avventura", "avventura"),
        Genre("Azione", "azione"),
        Genre("Commedia", "commedia"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Drammatico", "drammatico"),
        Genre("Ecchi", "ecchi"),
        Genre("Fantasy", "fantasy"),
        Genre("Gender Bender", "gender-bender"),
        Genre("Harem", "harem"),
        Genre("Hentai", "hentai"),
        Genre("Horror", "horror"),
        Genre("Josei", "josei"),
        Genre("Lolicon", "lolicon"),
        Genre("Maturo", "maturo"),
        Genre("Mecha", "mecha"),
        Genre("Mistero", "mistero"),
        Genre("Psicologico", "psicologico"),
        Genre("Romantico", "romantico"),
        Genre("Sci-fi", "sci-fi"),
        Genre("Scolastico", "scolastico"),
        Genre("Seinen", "seinen"),
        Genre("Shotacon", "shotacon"),
        Genre("Shoujo", "shoujo"),
        Genre("Shoujo Ai", "shoujo-ai"),
        Genre("Shounen", "shounen"),
        Genre("Shounen Ai", "shounen-ai"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Smut", "smut"),
        Genre("Soprannaturale", "soprannaturale"),
        Genre("Sport", "sport"),
        Genre("Storico", "storico"),
        Genre("Tragico", "tragico"),
        Genre("Yaoi", "yaoi"),
        Genre("Yuri", "yuri"),
    )
    protected fun getTypesList() = listOf(
        MType("Manga", "manga"),
        MType("Manhua", "manhua"),
        MType("Manhwa", "manhwa"),
        MType("Oneshot", "oneshot"),
        MType("Thai", "thai"),
        MType("Vietnamita", "vietnamese"),
    )
    protected fun getStatusList() = listOf(
        Status("In corso", "ongoing"),
        Status("Finito", "completed"),
        Status("Droppato", "dropped"),
        Status("In pausa", "paused"),
        Status("Cancellato", "canceled"),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
