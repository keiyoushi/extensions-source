package eu.kanade.tachiyomi.extension.es.lectortmo

import android.annotation.SuppressLint
import android.content.SharedPreferences
import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

private data class NsfwOption(
    val key: String,
    val title: String,
    val genreId: String,
)

class LectorTmo :
    ParsedHttpSource(),
    ConfigurableSource {

    override val id = 4146344224513899730

    override val name = "TuMangaOnline"

    override val baseUrl = "https://zonatmo.com"

    override val lang = "es"

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val supportsLatest = true

    private val json = Json { ignoreUnknownKeys = true }

    private val nsfwOptions = listOf(
        NsfwOption(NSFW_ECCHI, "Ecchi", "6"),
        NsfwOption(NSFW_GIRLS_LOVE, "Girls Love", "17"),
        NsfwOption(NSFW_BOYS_LOVE, "Boys Love", "18"),
        NsfwOption(NSFW_HAREM, "Harem", "19"),
        NsfwOption(NSFW_TRAP, "Trap", "94"),
    )

    // Needed to ignore the referer header in WebView
    private val tmoHeaders = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .build()

    private fun OkHttpClient.Builder.ignoreAllSSLErrors(): OkHttpClient.Builder {
        val naiveTrustManager =
            @SuppressLint("CustomX509TrustManager")
            object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) = Unit
            }

        val insecureSocketFactory = SSLContext.getInstance("SSL").apply {
            val trustAllCerts = arrayOf<TrustManager>(naiveTrustManager)
            init(null, trustAllCerts, SecureRandom())
        }.socketFactory

        sslSocketFactory(insecureSocketFactory, naiveTrustManager)
        hostnameVerifier { _, _ -> true }
        return this
    }

    override val client: OkHttpClient by lazy {
        network.cloudflareClient.newBuilder()
            .ignoreAllSSLErrors()
            .rateLimit(3, 1, TimeUnit.SECONDS)
            .build()
    }

    private var lastCFDomain: String = ""

    // Used on all requests except image requests
    private val safeClient: OkHttpClient by lazy {
        network.cloudflareClient.newBuilder()
            .addInterceptor { chain ->
                if (!getSaveLastCFUrlPref()) return@addInterceptor chain.proceed(chain.request())
                val response = chain.proceed(chain.request())
                if (response.code in CF_ERROR_CODES && response.header("Server") in CF_SERVER_CHECK) {
                    lastCFDomain = response.request.url.toString()
                }
                response
            }
            .rateLimit(1, 3, TimeUnit.SECONDS)
            .build()
    }

    // Marks erotic content as false and excludes: Ecchi(6), GirlsLove(17), BoysLove(18), Harem(19), Trap(94) genders
    private fun getSFWParams(): List<Pair<String, String>> = buildList {
        add("erotic" to "false")

        if (preferences.getBoolean(SFW_GENERAL, false)) {
            nsfwOptions.forEach {
                add("exclude_genders[]" to it.genreId)
            }
        } else {
            nsfwOptions.forEach {
                if (preferences.getBoolean(it.key, false)) {
                    add("exclude_genders[]" to it.genreId)
                }
            }
        }
    }

    private fun libraryRequest(page: Int, orderItem: String): Request {
        val url = "$baseUrl/library".toHttpUrl().newBuilder()

        url.addQueryParameter("order_item", orderItem)
        url.addQueryParameter("order_dir", "desc")
        url.addQueryParameter("filter_by", "title")

        getSFWParams().forEach { (k, v) ->
            url.addQueryParameter(k, v)
        }

        url.addQueryParameter("_pg", "1")
        url.addQueryParameter("page", page.toString())

        return GET(url.build(), tmoHeaders)
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = safeClient.newCall(popularMangaRequest(page))
        .asObservableSuccess()
        .map { popularMangaParse(it) }

    override fun popularMangaRequest(page: Int) = libraryRequest(page, "likes_count")

    override fun popularMangaNextPageSelector() = "a[rel='next']"

    override fun popularMangaSelector() = "div.element"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.select("div.element > a").let {
            setUrlWithoutDomain(it.attr("href").substringAfter(" "))
            title = it.select("h4.text-truncate").text()
            thumbnail_url = it.select("style").toString().substringAfter("('").substringBeforeLast("')")
        }
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> = safeClient.newCall(latestUpdatesRequest(page))
        .asObservableSuccess()
        .map { latestUpdatesParse(it) }

    override fun latestUpdatesRequest(page: Int) = libraryRequest(page, "creation")

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = if (query.startsWith(PREFIX_SLUG_SEARCH)) {
        val realQuery = query.removePrefix(PREFIX_SLUG_SEARCH)
        safeClient.newCall(searchMangaBySlugRequest(realQuery))
            .asObservableSuccess()
            .map { response ->
                val details = mangaDetailsParse(response)
                details.url = "/$PREFIX_LIBRARY/$realQuery"
                MangasPage(listOf(details), false)
            }
    } else {
        safeClient.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { searchMangaParse(it) }
    }

    private fun searchMangaBySlugRequest(slug: String) = GET("$baseUrl/$PREFIX_LIBRARY/$slug", tmoHeaders)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/library".toHttpUrl().newBuilder()

        url.addQueryParameter("title", query)

        getSFWParams().forEach { (k, v) ->
            url.addQueryParameter(k, v)
        }

        url.addQueryParameter("page", page.toString())
        url.addQueryParameter("_pg", "1")

        filters.forEach { filter ->
            when (filter) {
                is Types -> url.addQueryParameter("type", filter.toUriPart())
                is Demography -> url.addQueryParameter("demography", filter.toUriPart())
                is SortBy -> {
                    filter.state?.let {
                        url.addQueryParameter("order_item", SORTABLES[it.index].second)
                        url.addQueryParameter("order_dir", if (it.ascending) "asc" else "desc")
                    }
                }
                is ContentTypeList -> {
                    filter.state.forEach { content ->
                        when (content.state) {
                            Filter.TriState.STATE_IGNORE -> {}
                            Filter.TriState.STATE_INCLUDE -> url.addQueryParameter(content.id, "true")
                            Filter.TriState.STATE_EXCLUDE -> url.addQueryParameter(content.id, "false")
                        }
                    }
                }
                is GenreList -> {
                    filter.state.forEach { genre ->
                        when (genre.state) {
                            Filter.TriState.STATE_INCLUDE ->
                                url.addQueryParameter("genders[]", genre.id)
                            Filter.TriState.STATE_EXCLUDE ->
                                url.addQueryParameter("exclude_genders[]", genre.id)
                            else -> {}
                        }
                    }
                }
                else -> Unit
            }
        }

        return GET(url.build(), tmoHeaders)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun getMangaUrl(manga: SManga): String {
        if (lastCFDomain.isNotEmpty()) return lastCFDomain.also { lastCFDomain = "" }
        return super.getMangaUrl(manga)
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = safeClient.newCall(mangaDetailsRequest(manga))
        .asObservableSuccess()
        .map { mangaDetailsParse(it).apply { initialized = true } }

    override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl + manga.url, tmoHeaders)

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select("h2.element-subtitle").text()
        document.select("h5.card-title").let {
            author = it.first()?.attr("title")?.substringAfter(", ")
            artist = it.last()?.attr("title")?.substringAfter(", ")
        }
        genre = buildList {
            addAll(document.select("a.py-2").eachText())
            document.selectFirst("h1.book-type")?.text()
                ?.replaceFirstChar { it.uppercase() }
                ?.also(::add)
        }.joinToString()
        description = document.select("p.element-description").text()
        status = parseStatus(document.select("span.book-status").text())
        thumbnail_url = document.select(".book-thumbnail").attr("src")
    }

    private fun parseStatus(status: String) = when {
        status.contains("Publicándose") -> SManga.ONGOING
        status.contains("Pausado") -> SManga.ON_HIATUS
        status.contains("Cancelado") -> SManga.CANCELLED
        status.contains("Finalizado") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private val oneShotChapterName = "One Shot"

    override fun getChapterUrl(chapter: SChapter): String {
        if (lastCFDomain.isNotEmpty()) return lastCFDomain.also { lastCFDomain = "" }
        return super.getChapterUrl(chapter)
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = safeClient.newCall(chapterListRequest(manga))
        .asObservableSuccess()
        .map { chapterListParse(it) }

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
                chapterScanlator.firstOrNull()?.let { chapters.add(chapterFromElement(it, chapterName)) }
            }
        }
        return chapters
    }

    private val oneShotChapterListSelector = "div.chapter-list-element > ul.list-group li.list-group-item"

    private val regularChapterListSelector = "div.chapters > ul.list-group li.p-0.list-group-item"

    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()

    private fun chapterFromElement(element: Element, chName: String) = SChapter.create().apply {
        url = element.select("div.row > .text-right > a").attr("href")
        name = chName
        scanlator = element.select("div.col-md-6.text-truncate").text()
        date_upload = element.select("span.badge.badge-primary.p-2").first()?.text()?.let {
            parseChapterDate(it)
        } ?: 0
    }

    private fun parseChapterDate(date: String): Long = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date)?.time ?: 0

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = safeClient.newCall(pageListRequest(chapter))
        .asObservableSuccess()
        .map { pageListParse(it) }

    override fun pageListRequest(chapter: SChapter) = GET(chapter.url, tmoHeaders)

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
            val dirPath = DIRPATH_REGEX.find(it)?.groupValues?.get(1)
            val images = IMAGES_REGEX.find(it)?.groupValues?.get(1)?.split(",")?.map { img ->
                img.trim().removeSurrounding("\"")
            }
            if (dirPath != null && images != null) {
                return images.mapIndexed { i, img -> Page(i, doc.location(), "$dirPath$img") }
            }
        }

        return doc.select("div.viewer-container img:not(noscript img)").mapIndexed { i, img ->
            Page(i, doc.location(), img.imgAttr())
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
            val url = """window\.location\.replace\(['"](.+)['"]\)""".toRegex()
                .find(script2.data())?.groupValues?.get(1)?.unescapeUrl()
            if (url != null) return redirectToReadPage(client.newCall(GET(url, redirectHeaders)).execute().asJsoup())
        }

        if (script3 != null) {
            val url = """redirectUrl\s*=\s*'(.+)'""".toRegex()
                .find(script3.data())?.groupValues?.get(1)?.unescapeUrl()
            if (url != null) return redirectToReadPage(client.newCall(GET(url, redirectHeaders)).execute().asJsoup())
        }

        if (script4 != null) {
            val url = script4.attr("value").unescapeUrl()
            return redirectToReadPage(client.newCall(GET(url, redirectHeaders)).execute().asJsoup())
        }

        if (script5 != null) {
            val url = """;[^.]location\.replace\(['"](.+)['"]\)""".toRegex()
                .find(script5.data())?.groupValues?.get(1)?.unescapeUrl()
            if (url != null) return redirectToReadPage(client.newCall(GET(url, redirectHeaders)).execute().asJsoup())
        }

        return document
    }

    private fun Element.imgAttr(): String = if (this.hasAttr("data-src")) this.attr("abs:data-src") else this.attr("abs:src")

    private fun String.unescapeUrl(): String = if (this.startsWith("http:\\/\\/") || this.startsWith("https:\\/\\/")) {
        this.replace("\\/", "/")
    } else {
        this
    }

    override fun imageRequest(page: Page) = GET(
        url = page.imageUrl!!,
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

    private class FilterBy :
        UriPartFilter(
            "Buscar por",
            arrayOf(
                Pair("Título", "title"),
                Pair("Autor", "author"),
                Pair("Compañia", "company"),
            ),
        )

    class SortBy :
        Filter.Sort(
            "Ordenar por",
            SORTABLES.map { it.first }.toTypedArray(),
            Selection(0, false),
        )

    private class Types :
        UriPartFilter(
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

    private class Demography :
        UriPartFilter(
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

    private fun getScanlatorPref(): Boolean = preferences.getBoolean(SCANLATOR_PREF, SCANLATOR_PREF_DEFAULT_VALUE)

    private fun getSaveLastCFUrlPref(): Boolean = preferences.getBoolean(SAVE_LAST_CF_URL_PREF, SAVE_LAST_CF_URL_PREF_DEFAULT_VALUE)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val ctx = screen.context

        fun checkBox(key: String, title: String, summary: String? = null, default: Boolean = false) = CheckBoxPreference(ctx).apply {
            this.key = key
            this.title = title
            summary?.let { this.summary = it }
            setDefaultValue(default)
        }

        val nsfwGeneral = checkBox(
            SFW_GENERAL,
            "Ocultar todo el contenido NSFW",
            "Bloquea automáticamente Ecchi, GL, BL, Harem y Trap",
        )

        val subToggles = nsfwOptions.map {
            checkBox(it.key, "    • Ocultar ${it.title}")
        }

        fun updateState(enabled: Boolean) {
            subToggles.forEach { it.setEnabled(!enabled) }
        }

        updateState(preferences.getBoolean(SFW_GENERAL, false))

        nsfwGeneral.setOnPreferenceChangeListener { _, newValue ->
            updateState(newValue as Boolean)
            true
        }

        screen.addPreference(nsfwGeneral)
        subToggles.forEach { screen.addPreference(it) }

        screen.addPreference(
            checkBox(
                SCANLATOR_PREF,
                SCANLATOR_PREF_TITLE,
                SCANLATOR_PREF_SUMMARY,
                SCANLATOR_PREF_DEFAULT_VALUE,
            ),
        )

        screen.addPreference(
            checkBox(
                SAVE_LAST_CF_URL_PREF,
                SAVE_LAST_CF_URL_PREF_TITLE,
                SAVE_LAST_CF_URL_PREF_SUMMARY,
                SAVE_LAST_CF_URL_PREF_DEFAULT_VALUE,
            ),
        )
    }

    companion object {
        private val DIRPATH_REGEX = """var\s+dirPath\s*=\s*'(.*?)'\s*;""".toRegex()
        private val IMAGES_REGEX = """var\s+images\s*=.*\[(.*?)\]\s*'\s*\)\s*;""".toRegex()

        private const val SCANLATOR_PREF = "scanlatorPref"
        private const val SCANLATOR_PREF_TITLE = "Mostrar todos los scanlator"
        private const val SCANLATOR_PREF_SUMMARY = "Se mostraran capítulos repetidos pero con diferentes Scanlators"
        private const val SCANLATOR_PREF_DEFAULT_VALUE = true

        private const val SFW_GENERAL = "pref_sfw_general"
        private const val NSFW_ECCHI = "pref_nsfw_ecchi"
        private const val NSFW_GIRLS_LOVE = "pref_nsfw_girls_love"
        private const val NSFW_BOYS_LOVE = "pref_nsfw_boys_love"
        private const val NSFW_HAREM = "pref_nsfw_harem"
        private const val NSFW_TRAP = "pref_nsfw_trap"

        private const val SAVE_LAST_CF_URL_PREF = "saveLastCFUrlPreference"
        private const val SAVE_LAST_CF_URL_PREF_TITLE = "Guardar la última URL con error de Cloudflare"
        private const val SAVE_LAST_CF_URL_PREF_SUMMARY = "Guarda la última URL con error de Cloudflare para que se pueda acceder a ella al abrir la serie en WebView."
        private const val SAVE_LAST_CF_URL_PREF_DEFAULT_VALUE = true

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
        private val CF_SERVER_CHECK = listOf("cloudflare-nginx", "cloudflare")
    }
}
