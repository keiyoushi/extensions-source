package eu.kanade.tachiyomi.extension.es.tmohentai

import android.app.Application
import android.content.SharedPreferences
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TMOHentai : ConfigurableSource, ParsedHttpSource() {

    override val name = "TMOHentai"

    override val baseUrl = "https://tmohentai.com"

    override val lang = "es"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 1, 2)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/section/all?view=list&page=$page&order=popularity&order-dir=desc&search[searchText]=&search[searchBy]=name&type=all", headers)

    override fun popularMangaSelector() = "table > tbody > tr[data-toggle=popover]"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.select("tr").let {
            title = it.attr("data-title")
            thumbnail_url = it.attr("data-content").substringAfter("src=\"").substringBeforeLast("\"")
            setUrlWithoutDomain(it.select("td.text-left > a").attr("href"))
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    override fun popularMangaNextPageSelector() = "a[rel=next]"

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/section/all?view=list&page=$page&order=publication_date&order-dir=desc&search[searchText]=&search[searchBy]=name&type=all", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val parsedInformation = document.select("div.row > div.panel.panel-primary").text()
        val authorAndArtist = parsedInformation.substringAfter("Groups").substringBefore("Magazines").trim()

        title = document.select("h3.truncate").text()
        thumbnail_url = document.select("img.content-thumbnail-cover").attr("src")
        author = authorAndArtist
        artist = authorAndArtist
        description = "Sin descripción"
        status = SManga.UNKNOWN
        genre = parsedInformation.substringAfter("Genders").substringBefore("Tags").trim().split(" ").joinToString {
            it
        }
    }

    override fun chapterListSelector() = "div#app > div.container"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val parsedInformation = element.select("div.row > div.panel.panel-primary").text()

        name = element.select("h3.truncate").text()
        scanlator = parsedInformation.substringAfter("By").substringBefore("Language").trim()

        var currentUrl = element.select("a.pull-right.btn.btn-primary").attr("href")

        if (currentUrl.contains("/1")) {
            currentUrl = currentUrl.substringBeforeLast("/")
        }

        setUrlWithoutDomain(currentUrl)
        // date_upload = no date in the web
    }

    // "/cascade" to get all images
    override fun pageListRequest(chapter: SChapter): Request {
        val currentUrl = chapter.url
        val newUrl = if (getPageMethodPref() == "cascade" && currentUrl.contains("paginated")) {
            currentUrl.substringBefore("paginated") + "cascade"
        } else if (getPageMethodPref() == "paginated" && currentUrl.contains("cascade")) {
            currentUrl.substringBefore("cascade") + "paginated"
        } else {
            currentUrl
        }

        return GET("$baseUrl$newUrl", headers)
    }

    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        if (getPageMethodPref() == "cascade") {
            document.select("div#content-images img.content-image").forEach {
                add(Page(size, "", it.attr("abs:data-original")))
            }
        } else {
            val pageList = document.select("select#select-page").first()!!.select("option").map { it.attr("value").toInt() }
            val url = document.baseUri()

            pageList.forEach {
                add(Page(it, "$url/$it"))
            }
        }
    }

    override fun imageUrlParse(document: Document): String = document.select("div#content-images img.content-image").attr("abs:data-original")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/section/all?view=list".toHttpUrl().newBuilder()

        url.addQueryParameter("search[searchText]", query)
        url.addQueryParameter("page", page.toString())

        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is Types -> {
                    url.addQueryParameter("type", filter.toUriPart())
                }
                is GenreList -> {
                    filter.state
                        .filter { genre -> genre.state }
                        .forEach { genre -> url.addQueryParameter("genders[]", genre.id) }
                }
                is FilterBy -> {
                    url.addQueryParameter("search[searchBy]", filter.toUriPart())
                }
                is SortBy -> {
                    if (filter.state != null) {
                        url.addQueryParameter("order", SORTABLES[filter.state!!.index].second)
                        url.addQueryParameter(
                            "order-dir",
                            if (filter.state!!.ascending) { "asc" } else { "desc" },
                        )
                    }
                }
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    private fun searchMangaByIdRequest(id: String) = GET("$baseUrl/$PREFIX_CONTENTS/$id", headers)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_ID_SEARCH)) {
            val realQuery = query.removePrefix(PREFIX_ID_SEARCH)

            client.newCall(searchMangaByIdRequest(realQuery))
                .asObservableSuccess()
                .map { response ->
                    val details = mangaDetailsParse(response)
                    details.url = "/$PREFIX_CONTENTS/$realQuery"
                    MangasPage(listOf(details), false)
                }
        } else {
            client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { response ->
                    searchMangaParse(response)
                }
        }
    }

    private class Genre(name: String, val id: String) : Filter.CheckBox(name)

    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Géneros", genres)

    override fun getFilterList() = FilterList(
        Types(),
        Filter.Separator(),
        FilterBy(),
        SortBy(),
        Filter.Separator(),
        GenreList(getGenreList()),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private class Types : UriPartFilter(
        "Filtrar por tipo",
        arrayOf(
            Pair("Ver todos", "all"),
            Pair("Manga", "hentai"),
            Pair("Light Hentai", "light-hentai"),
            Pair("Doujinshi", "doujinshi"),
            Pair("One-shot", "one-shot"),
            Pair("Other", "otro"),
        ),
    )

    private class FilterBy : UriPartFilter(
        "Campo de orden",
        arrayOf(
            Pair("Nombre", "name"),
            Pair("Artista", "artist"),
            Pair("Revista", "magazine"),
            Pair("Tag", "tag"),
        ),
    )

    class SortBy : Filter.Sort(
        "Ordenar por",
        SORTABLES.map { it.first }.toTypedArray(),
        Selection(2, false),
    )

    /**
     * Last check: 13/02/2023
     * https://tmohentai.com/section/hentai
     *
     * Array.from(document.querySelectorAll('#advancedSearch .list-group .list-group-item'))
     * .map(a => `Genre("${a.querySelector('span').innerText.replace(' ', '')}", "${a.querySelector('input').value}")`).join(',\n')
     */
    private fun getGenreList() = listOf(
        Genre("Romance", "1"),
        Genre("Fantasy", "2"),
        Genre("Comedy", "3"),
        Genre("Parody", "4"),
        Genre("Student", "5"),
        Genre("Adventure", "6"),
        Genre("Milf", "7"),
        Genre("Orgy", "8"),
        Genre("Big Breasts", "9"),
        Genre("Bondage", "10"),
        Genre("Tentacles", "11"),
        Genre("Incest", "12"),
        Genre("Ahegao", "13"),
        Genre("Bestiality", "14"),
        Genre("Futanari", "15"),
        Genre("Rape", "16"),
        Genre("Monsters", "17"),
        Genre("Pregnant", "18"),
        Genre("Small Breast", "19"),
        Genre("Bukkake", "20"),
        Genre("Femdom", "21"),
        Genre("Fetish", "22"),
        Genre("Forced", "23"),
        Genre("3D", "24"),
        Genre("Furry", "25"),
        Genre("Adultery", "26"),
        Genre("Anal", "27"),
        Genre("FootJob", "28"),
        Genre("BlowJob", "29"),
        Genre("Toys", "30"),
        Genre("Vanilla", "31"),
        Genre("Colour", "32"),
        Genre("Uncensored", "33"),
        Genre("Netorare", "34"),
        Genre("Virgin", "35"),
        Genre("Cheating", "36"),
        Genre("Harem", "37"),
        Genre("Horror", "38"),
        Genre("Lolicon", "39"),
        Genre("Mature", "40"),
        Genre("Nympho", "41"),
        Genre("Public Sex", "42"),
        Genre("Sport", "43"),
        Genre("Domination", "44"),
        Genre("Tsundere", "45"),
        Genre("Yandere", "46"),
    )

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val pageMethodPref = androidx.preference.ListPreference(screen.context).apply {
            key = PAGE_METHOD_PREF
            title = PAGE_METHOD_PREF_TITLE
            entries = arrayOf("Cascada", "Páginado")
            entryValues = arrayOf("cascade", "paginated")
            summary = PAGE_METHOD_PREF_SUMMARY
            setDefaultValue(PAGE_METHOD_PREF_DEFAULT_VALUE)

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val setting = preferences.edit().putString(PAGE_METHOD_PREF, newValue as String).commit()
                    setting
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        screen.addPreference(pageMethodPref)
    }

    private fun getPageMethodPref() = preferences.getString(PAGE_METHOD_PREF, PAGE_METHOD_PREF_DEFAULT_VALUE)

    companion object {
        private const val PAGE_METHOD_PREF = "pageMethodPref"
        private const val PAGE_METHOD_PREF_TITLE = "Método de descarga de imágenes"
        private const val PAGE_METHOD_PREF_SUMMARY = "Puede corregir errores al cargar las imágenes.\nConfiguración actual: %s"
        private const val PAGE_METHOD_PREF_CASCADE = "cascade"
        private const val PAGE_METHOD_PREF_DEFAULT_VALUE = PAGE_METHOD_PREF_CASCADE

        const val PREFIX_CONTENTS = "contents"
        const val PREFIX_ID_SEARCH = "id:"

        private val SORTABLES = listOf(
            Pair("Alfabético", "alphabetic"),
            Pair("Creación", "publication_date"),
            Pair("Popularidad", "popularity"),
        )
    }
}
