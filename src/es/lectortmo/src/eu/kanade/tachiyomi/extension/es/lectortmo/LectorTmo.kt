package eu.kanade.tachiyomi.extension.es.lectortmo

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

abstract class LectorTmo(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    private val rateLimitClient: OkHttpClient,
) : ParsedHttpSource(), ConfigurableSource {

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val supportsLatest = true

    // Needed to ignore the referer header in WebView
    private val tmoHeaders = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .build()

    protected open val imageCDNUrls = arrayOf(
        "https://img1.japanreader.com",
        "https://japanreader.com",
        "https://imgtmo.com",
    )

    private fun OkHttpClient.Builder.rateLimitImageCDNs(hosts: Array<String>, permits: Int, period: Long): OkHttpClient.Builder {
        hosts.forEach { host ->
            rateLimitHost(host.toHttpUrl(), permits, period)
        }
        return this
    }

    private fun OkHttpClient.Builder.ignoreAllSSLErrors(): OkHttpClient.Builder {
        val naiveTrustManager = @SuppressLint("CustomX509TrustManager")
        object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) = Unit
        }

        val insecureSocketFactory = SSLContext.getInstance("TLSv1.2").apply {
            val trustAllCerts = arrayOf<TrustManager>(naiveTrustManager)
            init(null, trustAllCerts, SecureRandom())
        }.socketFactory

        sslSocketFactory(insecureSocketFactory, naiveTrustManager)
        hostnameVerifier { _, _ -> true }
        return this
    }

    private val ignoreSslClient: OkHttpClient by lazy {
        rateLimitClient.newBuilder()
            .ignoreAllSSLErrors()
            .followRedirects(false)
            .rateLimit(
                preferences.getString(IMAGE_CDN_RATELIMIT_PREF, IMAGE_CDN_RATELIMIT_PREF_DEFAULT_VALUE)!!.toInt(),
                60,
            )
            .build()
    }

    private var lastCFDomain: String = ""
    override val client: OkHttpClient by lazy {
        rateLimitClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url
                if (url.fragment == "imagereq") {
                    return@addInterceptor ignoreSslClient.newCall(request).execute()
                }
                chain.proceed(request)
            }
            .addInterceptor { chain ->
                if (!getSaveLastCFUrlPref()) return@addInterceptor chain.proceed(chain.request())
                val request = chain.request()
                val response = chain.proceed(request)
                if (response.code in CF_ERROR_CODES && response.header("Server") in CF_SERVER_CHECK) {
                    lastCFDomain = response.request.url.toString()
                }
                response
            }
            .rateLimitHost(
                baseUrl.toHttpUrl(),
                preferences.getString(WEB_RATELIMIT_PREF, WEB_RATELIMIT_PREF_DEFAULT_VALUE)!!.toInt(),
                60,
            )
            .rateLimitImageCDNs(
                imageCDNUrls,
                preferences.getString(IMAGE_CDN_RATELIMIT_PREF, IMAGE_CDN_RATELIMIT_PREF_DEFAULT_VALUE)!!.toInt(),
                60,
            )
            .build()
    }

    // Marks erotic content as false and excludes: Ecchi(6), GirlsLove(17), BoysLove(18), Harem(19), Trap(94) genders
    private fun getSFWUrlPart(): String = if (getSFWModePref()) "&exclude_genders%5B%5D=6&exclude_genders%5B%5D=17&exclude_genders%5B%5D=18&exclude_genders%5B%5D=19&exclude_genders%5B%5D=94&erotic=false" else ""

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/library?order_item=likes_count&order_dir=desc&filter_by=title${getSFWUrlPart()}&_pg=1&page=$page", tmoHeaders)

    override fun popularMangaNextPageSelector() = "a[rel='next']"

    override fun popularMangaSelector() = "div.element"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.select("div.element > a").let {
            setUrlWithoutDomain(it.attr("href").substringAfter(" "))
            title = it.select("h4.text-truncate").text()
            thumbnail_url = it.select("style").toString().substringAfter("('").substringBeforeLast("')")
        }
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/library?order_item=creation&order_dir=desc&filter_by=title${getSFWUrlPart()}&_pg=1&page=$page", tmoHeaders)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_SLUG_SEARCH)) {
            val realQuery = query.removePrefix(PREFIX_SLUG_SEARCH)

            client.newCall(searchMangaBySlugRequest(realQuery))
                .asObservableSuccess()
                .map { response ->
                    val details = mangaDetailsParse(response)
                    details.url = "/$PREFIX_LIBRARY/$realQuery"
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

    private fun searchMangaBySlugRequest(slug: String) = GET("$baseUrl/$PREFIX_LIBRARY/$slug", tmoHeaders)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/library".toHttpUrl().newBuilder()
        url.addQueryParameter("title", query)
        if (getSFWModePref()) {
            SFW_MODE_PREF_EXCLUDE_GENDERS.forEach { gender ->
                url.addQueryParameter("exclude_genders[]", gender)
            }
            url.addQueryParameter("erotic", "false")
        }
        url.addQueryParameter("page", page.toString())
        url.addQueryParameter("_pg", "1") // Extra Query to Prevent Scrapping aka without it = 403
        filters.forEach { filter ->
            when (filter) {
                is Types -> {
                    url.addQueryParameter("type", filter.toUriPart())
                }
                is Demography -> {
                    url.addQueryParameter("demography", filter.toUriPart())
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
                is ContentTypeList -> {
                    filter.state.forEach { content ->
                        if (!getSFWModePref() || (getSFWModePref() && content.id != "erotic")) {
                            when (content.state) {
                                Filter.TriState.STATE_IGNORE -> url.addQueryParameter(content.id, "")
                                Filter.TriState.STATE_INCLUDE -> url.addQueryParameter(content.id, "true")
                                Filter.TriState.STATE_EXCLUDE -> url.addQueryParameter(content.id, "false")
                            }
                        }
                    }
                }
                is GenreList -> {
                    filter.state.forEach { genre ->
                        when (genre.state) {
                            Filter.TriState.STATE_INCLUDE -> url.addQueryParameter("genders[]", genre.id)
                            Filter.TriState.STATE_EXCLUDE -> url.addQueryParameter("exclude_genders[]", genre.id)
                        }
                    }
                }
                else -> {}
            }
        }
        return GET(url.build(), tmoHeaders)
    }
    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun getMangaUrl(manga: SManga): String {
        if (lastCFDomain.isNotEmpty()) return lastCFDomain.also { lastCFDomain = "" }
        return super.getMangaUrl(manga)
    }

    override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl + manga.url, tmoHeaders)

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select("h2.element-subtitle").text()
        document.select("h5.card-title").let {
            author = it.first()?.attr("title")?.substringAfter(", ")
            artist = it.last()?.attr("title")?.substringAfter(", ")
        }
        genre = document.select("a.py-2").joinToString(", ") {
            it.text()
        }
        description = document.select("p.element-description").text()
        status = parseStatus(document.select("span.book-status").text())
        thumbnail_url = document.select(".book-thumbnail").attr("src")
    }

    protected fun parseStatus(status: String) = when {
        status.contains("Publicándose") -> SManga.ONGOING
        status.contains("Finalizado") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    protected open val oneShotChapterName = "One Shot"

    override fun getChapterUrl(chapter: SChapter): String {
        if (lastCFDomain.isNotEmpty()) return lastCFDomain.also { lastCFDomain = "" }
        return super.getChapterUrl(chapter)
    }

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        // One-shot
        if (document.select("div.chapters").isEmpty()) {
            return document.select(oneShotChapterListSelector).map { chapterFromElement(it, oneShotChapterName) }
        }

        // Regular list of chapters
        val chapters = mutableListOf<SChapter>()
        document.select(regularChapterListSelector).forEach { chapelement ->
            val chapterName = chapelement.select("div.col-10.text-truncate").text().replace("&nbsp;", " ").trim()
            val chapterScanlator = chapelement.select("ul.chapter-list > li")
            if (getScanlatorPref()) {
                chapterScanlator.forEach { chapters.add(chapterFromElement(it, chapterName)) }
            } else {
                chapterScanlator.first { chapters.add(chapterFromElement(it, chapterName)) }
            }
        }
        return chapters
    }

    protected open val oneShotChapterListSelector = "div.chapter-list-element > ul.list-group li.list-group-item"

    protected open val regularChapterListSelector = "div.chapters > ul.list-group li.p-0.list-group-item"

    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()

    protected open fun chapterFromElement(element: Element, chName: String) = SChapter.create().apply {
        url = element.select("div.row > .text-right > a").attr("href")
        name = chName
        scanlator = element.select("div.col-md-6.text-truncate").text()
        date_upload = element.select("span.badge.badge-primary.p-2").first()?.text()?.let {
            parseChapterDate(it)
        } ?: 0
    }

    protected open fun parseChapterDate(date: String): Long {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .parse(date)?.time ?: 0
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(chapter.url, tmoHeaders)
    }

    override fun pageListParse(document: Document): List<Page> {
        var doc = redirectToReadPage(document)

        val currentUrl = doc.location()

        val newUrl = if (!currentUrl.contains("cascade")) {
            currentUrl.substringBefore("paginated") + "cascade"
        } else {
            currentUrl
        }

        if (currentUrl != newUrl) {
            val redirectHeaders = super.headersBuilder()
                .set("Referer", doc.location())
                .build()
            doc = client.newCall(GET(newUrl, redirectHeaders)).execute().asJsoup()
        }
        val imagesScript = doc.selectFirst("script:containsData(var dirPath):containsData(var images)")

        imagesScript?.data()?.let {
            val dirPath = DIRPATH_REGEX.find(imagesScript.data())?.groupValues?.get(1)
            val images = IMAGES_REGEX.find(imagesScript.data())?.groupValues?.get(1)?.split(",")?.map { img ->
                img.trim().removeSurrounding("\"")
            }
            if (dirPath != null && images != null) {
                return images.mapIndexed { i, img ->
                    Page(i, doc.location(), "$dirPath$img")
                }
            }
        }

        doc.select("div.viewer-container img:not(noscript img)").let {
            return it.mapIndexed { i, img ->
                Page(i, doc.location(), img.imgAttr())
            }
        }
    }

    private tailrec fun redirectToReadPage(document: Document): Document {
        val script1 = document.selectFirst("script:containsData(uniqid)")
        val script2 = document.selectFirst("script:containsData(window.location.replace)")
        val script3 = document.selectFirst("script:containsData(redirectUrl)")
        val script4 = document.selectFirst("input#redir")
        val script5 = document.selectFirst("script:containsData(window.opener):containsData(location.replace)")

        val redirectHeaders = super.headersBuilder()
            .set("Referer", document.location())
            .build()

        if (script1 != null) {
            val data = script1.data()
            val regexParams = """\{\s*uniqid\s*:\s*'(.+)'\s*,\s*cascade\s*:\s*(.+)\s*\}""".toRegex()
            val regexAction = """form\.action\s*=\s*'(.+)'""".toRegex()
            val params = regexParams.find(data)
            val action = regexAction.find(data)?.groupValues?.get(1)?.unescapeUrl()

            if (params != null && action != null) {
                val formBody = FormBody.Builder()
                    .add("uniqid", params.groupValues[1])
                    .add("cascade", params.groupValues[2])
                    .build()
                return redirectToReadPage(client.newCall(POST(action, redirectHeaders, formBody)).execute().asJsoup())
            }
        }

        if (script2 != null) {
            val data = script2.data()
            val regexRedirect = """window\.location\.replace\(['"](.+)['"]\)""".toRegex()
            val url = regexRedirect.find(data)?.groupValues?.get(1)?.unescapeUrl()

            if (url != null) {
                return redirectToReadPage(client.newCall(GET(url, redirectHeaders)).execute().asJsoup())
            }
        }

        if (script3 != null) {
            val data = script3.data()
            val regexRedirect = """redirectUrl\s*=\s*'(.+)'""".toRegex()
            val url = regexRedirect.find(data)?.groupValues?.get(1)?.unescapeUrl()

            if (url != null) {
                return redirectToReadPage(client.newCall(GET(url, redirectHeaders)).execute().asJsoup())
            }
        }

        if (script4 != null) {
            val url = script4.attr("value").unescapeUrl()

            return redirectToReadPage(client.newCall(GET(url, redirectHeaders)).execute().asJsoup())
        }

        if (script5 != null) {
            val data = script5.data()
            val regexRedirect = """;[^.]location\.replace\(['"](.+)['"]\)""".toRegex()
            val url = regexRedirect.find(data)?.groupValues?.get(1)?.unescapeUrl()

            if (url != null) {
                return redirectToReadPage(client.newCall(GET(url, redirectHeaders)).execute().asJsoup())
            }
        }

        return document
    }

    private fun Element.imgAttr(): String {
        return when {
            this.hasAttr("data-src") -> this.attr("abs:data-src")
            else -> this.attr("abs:src")
        }
    }

    private fun String.unescapeUrl(): String {
        return if (this.startsWith("http:\\/\\/") || this.startsWith("https:\\/\\/")) {
            this.replace("\\/", "/")
        } else {
            this
        }
    }

    override fun imageRequest(page: Page) = GET(
        url = page.imageUrl!! + "#imagereq",
        headers = headers.newBuilder()
            .set("Referer", page.url.substringBefore("news/"))
            .build(),
    )

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        FilterBy(),
        Filter.Separator(),
        SortBy(),
        Filter.Separator(),
        Types(),
        Demography(),
        ContentTypeList(getContentTypeList()),
        Filter.Separator(),
        GenreList(getGenreList()),
    )

    private class FilterBy : UriPartFilter(
        "Buscar por",
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

    private class Types : UriPartFilter(
        "Filtrar por tipo",
        arrayOf(
            Pair("Ver todo", ""),
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
            Pair("Ver todo", ""),
            Pair("Seinen", "seinen"),
            Pair("Shoujo", "shoujo"),
            Pair("Shounen", "shounen"),
            Pair("Josei", "josei"),
            Pair("Kodomo", "kodomo"),
        ),
    )

    private fun getContentTypeList() = listOf(
        ContentType("Webcomic", "webcomic"),
        ContentType("Yonkoma", "yonkoma"),
        ContentType("Amateur", "amateur"),
        ContentType("Erótico", "erotic"),
    )

    // Array.from(document.querySelectorAll('#books-genders .col-auto .custom-control'))
    // .map(a => `Genre("${a.querySelector('label').innerText}", "${a.querySelector('input').value}")`).join(',\n')
    // on ${baseUrl}/library
    // Last revision 02/04/2024 (mm/dd/yyyy)
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
        Genre("Trap", "94"),
    )

    protected fun getScanlatorPref(): Boolean = preferences.getBoolean(SCANLATOR_PREF, SCANLATOR_PREF_DEFAULT_VALUE)

    protected fun getSFWModePref(): Boolean = preferences.getBoolean(SFW_MODE_PREF, SFW_MODE_PREF_DEFAULT_VALUE)

    protected fun getSaveLastCFUrlPref(): Boolean = preferences.getBoolean(SAVE_LAST_CF_URL_PREF, SAVE_LAST_CF_URL_PREF_DEFAULT_VALUE)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val sfwModePref = CheckBoxPreference(screen.context).apply {
            key = SFW_MODE_PREF
            title = SFW_MODE_PREF_TITLE
            summary = SFW_MODE_PREF_SUMMARY
            setDefaultValue(SFW_MODE_PREF_DEFAULT_VALUE)
        }

        val scanlatorPref = CheckBoxPreference(screen.context).apply {
            key = SCANLATOR_PREF
            title = SCANLATOR_PREF_TITLE
            summary = SCANLATOR_PREF_SUMMARY
            setDefaultValue(SCANLATOR_PREF_DEFAULT_VALUE)
        }

        // Rate limit
        val apiRateLimitPreference = ListPreference(screen.context).apply {
            key = WEB_RATELIMIT_PREF
            title = WEB_RATELIMIT_PREF_TITLE
            summary = WEB_RATELIMIT_PREF_SUMMARY
            entries = ENTRIES_ARRAY
            entryValues = ENTRIES_ARRAY
            setDefaultValue(WEB_RATELIMIT_PREF_DEFAULT_VALUE)
        }

        val imgCDNRateLimitPreference = ListPreference(screen.context).apply {
            key = IMAGE_CDN_RATELIMIT_PREF
            title = IMAGE_CDN_RATELIMIT_PREF_TITLE
            summary = IMAGE_CDN_RATELIMIT_PREF_SUMMARY
            entries = ENTRIES_ARRAY
            entryValues = ENTRIES_ARRAY
            setDefaultValue(IMAGE_CDN_RATELIMIT_PREF_DEFAULT_VALUE)
        }

        val saveLastCFUrlPreference = CheckBoxPreference(screen.context).apply {
            key = SAVE_LAST_CF_URL_PREF
            title = SAVE_LAST_CF_URL_PREF_TITLE
            summary = SAVE_LAST_CF_URL_PREF_SUMMARY
            setDefaultValue(SAVE_LAST_CF_URL_PREF_DEFAULT_VALUE)
        }

        screen.addPreference(sfwModePref)
        screen.addPreference(scanlatorPref)
        screen.addPreference(apiRateLimitPreference)
        screen.addPreference(imgCDNRateLimitPreference)
        screen.addPreference(saveLastCFUrlPreference)
    }

    companion object {
        val DIRPATH_REGEX = """var\s+dirPath\s*=\s*'(.*?)'\s*;""".toRegex()
        val IMAGES_REGEX = """var\s+images\s*=.*\[(.*?)\]\s*'\s*\)\s*;""".toRegex()

        private const val SCANLATOR_PREF = "scanlatorPref"
        private const val SCANLATOR_PREF_TITLE = "Mostrar todos los scanlator"
        private const val SCANLATOR_PREF_SUMMARY = "Se mostraran capítulos repetidos pero con diferentes Scanlators"
        private const val SCANLATOR_PREF_DEFAULT_VALUE = true

        private const val SFW_MODE_PREF = "SFWModePref"
        private const val SFW_MODE_PREF_TITLE = "Ocultar contenido NSFW"
        private const val SFW_MODE_PREF_SUMMARY = "Ocultar el contenido erótico (puede que aún activandolo se sigan mostrando portadas o series NSFW). Ten en cuenta que al activarlo se ignoran filtros al explorar y buscar.\nLos filtros ignorados son: Filtrar por tipo de contenido (Erotico) y el Filtrar por generos: Ecchi, Boys Love, Girls Love, Harem y Trap."
        private const val SFW_MODE_PREF_DEFAULT_VALUE = false
        private val SFW_MODE_PREF_EXCLUDE_GENDERS = listOf("6", "17", "18", "19")

        private const val WEB_RATELIMIT_PREF = "webRatelimitPreference"
        private const val WEB_RATELIMIT_PREF_TITLE = "Ratelimit por minuto para el sitio web"
        private const val WEB_RATELIMIT_PREF_SUMMARY = "Este valor afecta la cantidad de solicitudes de red a la URL de TMO. Reducir este valor puede disminuir la posibilidad de obtener un error HTTP 429, pero la velocidad de descarga será más lenta. Se requiere reiniciar la app. \nValor actual: %s"
        private const val WEB_RATELIMIT_PREF_DEFAULT_VALUE = "8"

        private const val IMAGE_CDN_RATELIMIT_PREF = "imgCDNRatelimitPreference"
        private const val IMAGE_CDN_RATELIMIT_PREF_TITLE = "Ratelimit por minuto para descarga de imágenes"
        private const val IMAGE_CDN_RATELIMIT_PREF_SUMMARY = "Este valor afecta la cantidad de solicitudes de red para descargar imágenes. Reducir este valor puede disminuir errores al cargar imagenes, pero la velocidad de descarga será más lenta. Se requiere reiniciar la app. \nValor actual: %s"
        private const val IMAGE_CDN_RATELIMIT_PREF_DEFAULT_VALUE = "50"

        private const val SAVE_LAST_CF_URL_PREF = "saveLastCFUrlPreference"
        private const val SAVE_LAST_CF_URL_PREF_TITLE = "Guardar la última URL con error de Cloudflare"
        private const val SAVE_LAST_CF_URL_PREF_SUMMARY = "Guarda la última URL con error de Cloudflare para que se pueda acceder a ella al abrir la serie en WebView."
        private const val SAVE_LAST_CF_URL_PREF_DEFAULT_VALUE = true

        private val ENTRIES_ARRAY = listOf(1, 2, 3, 5, 6, 7, 8, 9, 10, 15, 20, 30, 40, 50, 100).map { i -> i.toString() }.toTypedArray()

        const val PREFIX_LIBRARY = "library"
        const val PREFIX_SLUG_SEARCH = "slug:"

        private val SORTABLES = listOf(
            Pair("Me gusta", "likes_count"),
            Pair("Alfabético", "alphabetically"),
            Pair("Puntuación", "score"),
            Pair("Creación", "creation"),
            Pair("Fecha estreno", "release_date"),
            Pair("Núm. Capítulos", "num_chapters"),
        )

        private val CF_ERROR_CODES = listOf(403, 503)
        private val CF_SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
    }
}
