package eu.kanade.tachiyomi.extension.es.lectormanga

import android.app.Application
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CountDownLatch

class LectorManga : ConfigurableSource, ParsedHttpSource() {

    override val name = "LectorManga"

    override val baseUrl = "https://lectormanga.com"

    override val lang = "es"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder {
        return Headers.Builder()
            .add("Referer", "$baseUrl/")
    }

    private val imageCDNUrls = arrayOf(
        "https://img1.followmanga.com",
        "https://img1.biggestchef.com",
        "https://img1.indalchef.com",
        "https://img1.recipesandcook.com",
        "https://img1.cyclingte.com",
        "https://img1.japanreader.com",
        "https://japanreader.com",
    )

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private fun OkHttpClient.Builder.rateLimitImageCDNs(hosts: Array<String>, permits: Int, period: Long): OkHttpClient.Builder {
        hosts.forEach { host ->
            rateLimitHost(host.toHttpUrlOrNull()!!, permits, period)
        }
        return this
    }

    private var loadWebView = true
    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimitHost(
            baseUrl.toHttpUrlOrNull()!!,
            preferences.getString(WEB_RATELIMIT_PREF, WEB_RATELIMIT_PREF_DEFAULT_VALUE)!!.toInt(),
            60,
        )
        .rateLimitImageCDNs(
            imageCDNUrls,
            preferences.getString(IMAGE_CDN_RATELIMIT_PREF, IMAGE_CDN_RATELIMIT_PREF_DEFAULT_VALUE)!!.toInt(),
            60,
        )
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url
            if (url.host.contains("japanreader.com") && loadWebView) {
                val handler = Handler(Looper.getMainLooper())
                val latch = CountDownLatch(1)
                var webView: WebView? = null
                handler.post {
                    val webview = WebView(Injekt.get<Application>())
                    webView = webview
                    webview.settings.domStorageEnabled = true
                    webview.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    webview.settings.useWideViewPort = false
                    webview.settings.loadWithOverviewMode = false

                    webview.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            latch.countDown()
                        }
                    }

                    val headers = mutableMapOf<String, String>()
                    headers["Referer"] = baseUrl

                    webview.loadUrl(url.toString(), headers)
                }

                latch.await()
                loadWebView = false
                handler.post { webView?.destroy() }
            }
            chain.proceed(request)
        }
        .build()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/library?order_item=likes_count&order_dir=desc&type=&filter_by=title&page=$page", headers)

    override fun popularMangaNextPageSelector() = "a[rel='next']"

    override fun popularMangaSelector() = ".col-6 .card"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        title = element.select("a").text()
        thumbnail_url = element.select("img").attr("src")
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/library?order_item=creation&order_dir=desc&page=$page", headers)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/library".toHttpUrlOrNull()!!.newBuilder()

        url.addQueryParameter("title", query)
        url.addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is Types -> {
                    url.addQueryParameter("type", filter.toUriPart())
                }
                is Demography -> {
                    url.addQueryParameter("demography", filter.toUriPart())
                }
                is FilterBy -> {
                    url.addQueryParameter("filter_by", filter.toUriPart())
                }
                is SortBy -> {
                    if (filter.state != null) {
                        url.addQueryParameter("order_item", SORTABLES[filter.state!!.index].second)
                        url.addQueryParameter(
                            "order_dir",
                            if (filter.state!!.ascending) { "asc" } else { "desc" },
                        )
                    }
                }
                is WebcomicFilter -> {
                    url.addQueryParameter(
                        "webcomic",
                        when (filter.state) {
                            Filter.TriState.STATE_INCLUDE -> "true"
                            Filter.TriState.STATE_EXCLUDE -> "false"
                            else -> ""
                        },
                    )
                }
                is FourKomaFilter -> {
                    url.addQueryParameter(
                        "yonkoma",
                        when (filter.state) {
                            Filter.TriState.STATE_INCLUDE -> "true"
                            Filter.TriState.STATE_EXCLUDE -> "false"
                            else -> ""
                        },
                    )
                }
                is AmateurFilter -> {
                    url.addQueryParameter(
                        "amateur",
                        when (filter.state) {
                            Filter.TriState.STATE_INCLUDE -> "true"
                            Filter.TriState.STATE_EXCLUDE -> "false"
                            else -> ""
                        },
                    )
                }
                is EroticFilter -> {
                    url.addQueryParameter(
                        "erotic",
                        when (filter.state) {
                            Filter.TriState.STATE_INCLUDE -> "true"
                            Filter.TriState.STATE_EXCLUDE -> "false"
                            else -> ""
                        },
                    )
                }
                is GenreList -> {
                    filter.state
                        .filter { genre -> genre.state }
                        .forEach { genre -> url.addQueryParameter("genders[]", genre.id) }
                }
                else -> {}
            }
        }

        return GET(url.build().toString(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select("h1:has(small)").text()
        genre = document.select("a.py-2").joinToString(", ") {
            it.text()
        }
        description = document.select(".col-12.mt-2").text()
        status = parseStatus(document.select(".status-publishing").text().orEmpty())
        thumbnail_url = document.select(".text-center img.img-fluid").attr("src")
    }

    private fun parseStatus(status: String) = when {
        status.contains("Publicándose") -> SManga.ONGOING
        status.contains("Finalizado") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> = mutableListOf<SChapter>().apply {
        val document = response.asJsoup()

        // One-shot
        if (document.select("#chapters").isEmpty()) {
            return document.select(oneShotChapterListSelector()).map { oneShotChapterFromElement(it) }
        }

        // Regular list of chapters
        val chapterNames = document.select("#chapters h4.text-truncate")
        val chapterInfos = document.select("#chapters .chapter-list")

        chapterNames.forEachIndexed { index, _ ->
            val scanlator = chapterInfos[index].select("li")
            if (getScanlatorPref()) {
                scanlator.forEach { add(regularChapterFromElement(chapterNames[index].text(), it)) }
            } else {
                scanlator.last { add(regularChapterFromElement(chapterNames[index].text(), it)) }
            }
        }
    }

    override fun chapterListSelector() = throw UnsupportedOperationException("Not used")

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException("Not used")

    private fun oneShotChapterListSelector() = "div.chapter-list-element > ul.list-group li.list-group-item"

    private fun oneShotChapterFromElement(element: Element) = SChapter.create().apply {
        url = element.select("div.row > .text-right > a").attr("href")
        name = "One Shot"
        scanlator = element.select("div.col-12.col-sm-12.col-md-4.text-truncate span").text()
        date_upload = element.select("span.badge.badge-primary.p-2").first()?.text()?.let { parseChapterDate(it) }
            ?: 0
    }

    private fun regularChapterFromElement(chapterName: String, info: Element) = SChapter.create().apply {
        url = info.select("div.row > .text-right > a").attr("href")
        name = chapterName
        scanlator = info.select("div.col-12.col-sm-12.col-md-4.text-truncate span").text()
        date_upload = info.select("span.badge.badge-primary.p-2").first()?.text()?.let {
            parseChapterDate(it)
        } ?: 0
    }

    private fun parseChapterDate(date: String): Long {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .parse(date)?.time ?: 0
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(chapter.url, headers)
    }

    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        var doc = redirectToReadPage(document)
        val currentUrl = doc.location()

        val newUrl = if (!currentUrl.contains("cascade")) {
            currentUrl.substringBefore("paginated") + "cascade"
        } else {
            currentUrl
        }

        if (currentUrl != newUrl) {
            doc = client.newCall(GET(newUrl, headers)).execute().asJsoup()
        }

        doc.select("div.viewer-container img:not(noscript img)").forEach {
            add(
                Page(
                    size,
                    doc.location(),
                    it.let {
                        if (it.hasAttr("data-src")) {
                            it.attr("abs:data-src")
                        } else {
                            it.attr("abs:src")
                        }
                    },
                ),
            )
        }
    }

    // Some chapters uses JavaScript to redirect to read page
    private fun redirectToReadPage(document: Document): Document {
        val script1 = document.selectFirst("script:containsData(uniqid)")
        val script2 = document.selectFirst("script:containsData(window.location.replace)")

        val redirectHeaders = Headers.Builder()
            .add("Referer", document.baseUri())
            .build()

        if (script1 != null) {
            val data = script1.data()
            val regexParams = """\{uniqid:'(.+)',cascade:(.+)\}""".toRegex()
            val regexAction = """form\.action\s?=\s?'(.+)'""".toRegex()
            val params = regexParams.find(data)!!
            val action = regexAction.find(data)!!.groupValues[1]

            val formBody = FormBody.Builder()
                .add("uniqid", params.groupValues[1])
                .add("cascade", params.groupValues[2])
                .build()

            return redirectToReadPage(client.newCall(POST(action, redirectHeaders, formBody)).execute().asJsoup())
        }

        if (script2 != null) {
            val data = script2.data()
            val regexRedirect = """window\.location\.replace\('(.+)'\)""".toRegex()
            val url = regexRedirect.find(data)!!.groupValues[1]

            return redirectToReadPage(client.newCall(GET(url, redirectHeaders)).execute().asJsoup())
        }

        return document
    }

    override fun imageRequest(page: Page) = GET(
        url = page.imageUrl!!,
        headers = headers.newBuilder()
            .removeAll("Referer")
            .add("Referer", page.url.substringBefore("news/"))
            .build(),
    )

    override fun imageUrlParse(document: Document) = throw Exception("Not Used")

    private fun searchMangaByIdRequest(id: String) = GET("$baseUrl/$MANGA_URL_CHUNK/$id", headers)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_ID_SEARCH)) {
            val realQuery = query.removePrefix(PREFIX_ID_SEARCH)

            client.newCall(searchMangaByIdRequest(realQuery))
                .asObservableSuccess()
                .map { response ->
                    val details = mangaDetailsParse(response)
                    details.url = "/$MANGA_URL_CHUNK/$realQuery"
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

    private class Types : UriPartFilter(
        "Filtrar por tipo",
        arrayOf(
            Pair("Ver todos", ""),
            Pair("Manga", "manga"),
            Pair("Manhua", "manhua"),
            Pair("Manhwa", "manhwa"),
            Pair("Novela", "novel"),
            Pair("One shot", "one_shot"),
            Pair("Doujinshi", "doujinshi"),
            Pair("Oel", "oel"),
        ),
    )

    private class Demography : UriPartFilter(
        "Filtrar por demografía",
        arrayOf(
            Pair("Ver todas", ""),
            Pair("Seinen", "seinen"),
            Pair("Shoujo", "shoujo"),
            Pair("Shounen", "shounen"),
            Pair("Josei", "josei"),
            Pair("Kodomo", "kodomo"),
        ),
    )

    private class FilterBy : UriPartFilter(
        "Campo de orden",
        arrayOf(
            Pair("Título", "title"),
            Pair("Autor", "author"),
            Pair("Compañia", "company"),
        ),
    )

    class SortBy : Filter.Sort(
        "Ordenar por",
        SORTABLES.map { it.first }.toTypedArray(),
        Selection(0, false),
    )

    private class WebcomicFilter : Filter.TriState("Webcomic")

    private class FourKomaFilter : Filter.TriState("Yonkoma")

    private class AmateurFilter : Filter.TriState("Amateur")

    private class EroticFilter : Filter.TriState("Erótico")

    private class Genre(name: String, val id: String) : Filter.CheckBox(name)

    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Filtrar por géneros", genres)

    override fun getFilterList() = FilterList(
        Types(),
        Demography(),
        Filter.Separator(),
        FilterBy(),
        SortBy(),
        Filter.Separator(),
        WebcomicFilter(),
        FourKomaFilter(),
        AmateurFilter(),
        EroticFilter(),
        GenreList(getGenreList()),
    )

    // Array.from(document.querySelectorAll('#advancedSearch .custom-checkbox'))
    // .map(a => `Genre("${a.querySelector('label').innerText}", "${a.querySelector('input').value}")`).join(',\n')
    // on https://lectormanga.com/library
    // Last revision 30/08/2021
    private fun getGenreList() = listOf(
        Genre("Acción", "1"),
        Genre("Aventura", "2"),
        Genre("Comedia", "3"),
        Genre("Drama", "4"),
        Genre("Recuentos de la vida", "5"),
        Genre("Ecchi", "6"),
        Genre("Fantasia", "7"),
        Genre("Magia", "8"),
        Genre("Sobrenatural", "9"),
        Genre("Horror", "10"),
        Genre("Misterio", "11"),
        Genre("Psicológico", "12"),
        Genre("Romance", "13"),
        Genre("Ciencia Ficción", "14"),
        Genre("Thriller", "15"),
        Genre("Deporte", "16"),
        Genre("Girls Love", "17"),
        Genre("Boys Love", "18"),
        Genre("Harem", "19"),
        Genre("Mecha", "20"),
        Genre("Supervivencia", "21"),
        Genre("Reencarnación", "22"),
        Genre("Gore", "23"),
        Genre("Apocalíptico", "24"),
        Genre("Tragedia", "25"),
        Genre("Vida Escolar", "26"),
        Genre("Historia", "27"),
        Genre("Militar", "28"),
        Genre("Policiaco", "29"),
        Genre("Crimen", "30"),
        Genre("Superpoderes", "31"),
        Genre("Vampiros", "32"),
        Genre("Artes Marciales", "33"),
        Genre("Samurái", "34"),
        Genre("Género Bender", "35"),
        Genre("Realidad Virtual", "36"),
        Genre("Ciberpunk", "37"),
        Genre("Musica", "38"),
        Genre("Parodia", "39"),
        Genre("Animación", "40"),
        Genre("Demonios", "41"),
        Genre("Familia", "42"),
        Genre("Extranjero", "43"),
        Genre("Niños", "44"),
        Genre("Realidad", "45"),
        Genre("Telenovela", "46"),
        Genre("Guerra", "47"),
        Genre("Oeste", "48"),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val scanlatorPref = androidx.preference.CheckBoxPreference(screen.context).apply {
            key = SCANLATOR_PREF
            title = SCANLATOR_PREF_TITLE
            summary = SCANLATOR_PREF_SUMMARY
            setDefaultValue(SCANLATOR_PREF_DEFAULT_VALUE)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean
                preferences.edit().putBoolean(SCANLATOR_PREF, checkValue).commit()
            }
        }

        // Rate limit
        val apiRateLimitPreference = androidx.preference.ListPreference(screen.context).apply {
            key = WEB_RATELIMIT_PREF
            title = WEB_RATELIMIT_PREF_TITLE
            summary = WEB_RATELIMIT_PREF_SUMMARY
            entries = ENTRIES_ARRAY
            entryValues = ENTRIES_ARRAY

            setDefaultValue(WEB_RATELIMIT_PREF_DEFAULT_VALUE)
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val setting = preferences.edit().putString(WEB_RATELIMIT_PREF, newValue as String).commit()
                    setting
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        val imgCDNRateLimitPreference = androidx.preference.ListPreference(screen.context).apply {
            key = IMAGE_CDN_RATELIMIT_PREF
            title = IMAGE_CDN_RATELIMIT_PREF_TITLE
            summary = IMAGE_CDN_RATELIMIT_PREF_SUMMARY
            entries = ENTRIES_ARRAY
            entryValues = ENTRIES_ARRAY

            setDefaultValue(IMAGE_CDN_RATELIMIT_PREF_DEFAULT_VALUE)
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val setting = preferences.edit().putString(IMAGE_CDN_RATELIMIT_PREF, newValue as String).commit()
                    setting
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        screen.addPreference(scanlatorPref)
        screen.addPreference(apiRateLimitPreference)
        screen.addPreference(imgCDNRateLimitPreference)
    }

    private fun getScanlatorPref(): Boolean = preferences.getBoolean(SCANLATOR_PREF, SCANLATOR_PREF_DEFAULT_VALUE)

    companion object {
        private const val SCANLATOR_PREF = "scanlatorPref"
        private const val SCANLATOR_PREF_TITLE = "Mostrar todos los scanlator"
        private const val SCANLATOR_PREF_SUMMARY = "Se mostraran capítulos repetidos pero con diferentes Scanlators"
        private const val SCANLATOR_PREF_DEFAULT_VALUE = true

        private const val WEB_RATELIMIT_PREF = "webRatelimitPreference"

        // Ratelimit permits per second for main website
        private const val WEB_RATELIMIT_PREF_TITLE = "Ratelimit por minuto para el sitio web"

        // This value affects network request amount to TMO url. Lower this value may reduce the chance to get HTTP 429 error, but loading speed will be slower too. Tachiyomi restart required. \nCurrent value: %s
        private const val WEB_RATELIMIT_PREF_SUMMARY = "Este valor afecta la cantidad de solicitudes de red a la URL de TMO. Reducir este valor puede disminuir la posibilidad de obtener un error HTTP 429, pero la velocidad de descarga será más lenta. Se requiere reiniciar Tachiyomi. \nValor actual: %s"
        private const val WEB_RATELIMIT_PREF_DEFAULT_VALUE = "8"

        private const val IMAGE_CDN_RATELIMIT_PREF = "imgCDNRatelimitPreference"

        // Ratelimit permits per second for image CDN
        private const val IMAGE_CDN_RATELIMIT_PREF_TITLE = "Ratelimit por minuto para descarga de imágenes"

        // This value affects network request amount for loading image. Lower this value may reduce the chance to get error when loading image, but loading speed will be slower too. Tachiyomi restart required. \nCurrent value: %s
        private const val IMAGE_CDN_RATELIMIT_PREF_SUMMARY = "Este valor afecta la cantidad de solicitudes de red para descargar imágenes. Reducir este valor puede disminuir errores al cargar imagenes, pero la velocidad de descarga será más lenta. Se requiere reiniciar Tachiyomi. \nValor actual: %s"
        private const val IMAGE_CDN_RATELIMIT_PREF_DEFAULT_VALUE = "50"

        private val ENTRIES_ARRAY = arrayOf("1", "2", "3", "5", "6", "7", "8", "9", "10", "15", "20", "30", "40", "50", "100")

        const val PREFIX_ID_SEARCH = "id:"
        const val MANGA_URL_CHUNK = "gotobook"

        private val SORTABLES = listOf(
            Pair("Me gusta", "likes_count"),
            Pair("Alfabético", "alphabetically"),
            Pair("Puntuación", "score"),
            Pair("Creación", "creation"),
            Pair("Fecha estreno", "release_date"),
        )
    }
}
