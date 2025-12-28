package eu.kanade.tachiyomi.extension.es.lectortmo

import android.annotation.SuppressLint
import android.content.Context
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
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

@Serializable
data class NsfwState(
    val ecchi: Boolean,
    val girlsLove: Boolean,
    val boysLove: Boolean,
    val harem: Boolean,
    val trap: Boolean,
)
class LectorTmo : ParsedHttpSource(), ConfigurableSource {

    override val id = 4146344224513899730

    override val name = "TuMangaOnline"

    override val baseUrl = "https://zonatmo.com"

    override val lang = "es"

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val supportsLatest = true

    // Needed to ignore the referer header in WebView
    private val tmoHeaders = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .build()

    private fun OkHttpClient.Builder.ignoreAllSSLErrors(): OkHttpClient.Builder {
        val naiveTrustManager = @SuppressLint("CustomX509TrustManager")
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

    // Used on all request except on image requests
    private val safeClient: OkHttpClient by lazy {
        network.cloudflareClient.newBuilder()
            .addInterceptor { chain ->
                if (!getSaveLastCFUrlPref()) return@addInterceptor chain.proceed(chain.request())
                val request = chain.request()
                val response = chain.proceed(request)
                if (response.code in CF_ERROR_CODES && response.header("Server") in CF_SERVER_CHECK) {
                    lastCFDomain = response.request.url.toString()
                }
                response
            }
            .rateLimit(1, 3, TimeUnit.SECONDS)
            .build()
    }

    // Marks erotic content as false and excludes: Ecchi(6), GirlsLove(17), BoysLove(18), Harem(19), Trap(94) genders
    private fun getSFWUrlPart(): String {
        val hidden = mutableListOf<String>()

        if (getSfwGeneral()) {
            // Oculta TODO el contenido NSFW
            hidden += listOf("6", "17", "18", "19", "94")
        } else {
            if (getNsfwEcchi()) hidden += "6"
            if (getNsfwGirlsLove()) hidden += "17"
            if (getNsfwBoysLove()) hidden += "18"
            if (getNsfwHarem()) hidden += "19"
            if (getNsfwTrap()) hidden += "94"
        }

        if (hidden.isEmpty()) return ""

        val params = hidden.joinToString("") { "&exclude_genders[]=$it" }

        val addErotic = getSfwGeneral() || getNsfwEcchi()

        return if (addErotic) {
            "$params&erotic=false"
        } else {
            params
        }
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return safeClient.newCall(popularMangaRequest(page))
            .asObservableSuccess()
            .map { response ->
                popularMangaParse(response)
            }
    }

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

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return safeClient.newCall(latestUpdatesRequest(page))
            .asObservableSuccess()
            .map { response ->
                latestUpdatesParse(response)
            }
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/library?order_item=creation&order_dir=desc&filter_by=title${getSFWUrlPart()}&_pg=1&page=$page", tmoHeaders)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_SLUG_SEARCH)) {
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
                .map { response ->
                    searchMangaParse(response)
                }
        }
    }

    private fun searchMangaBySlugRequest(slug: String) = GET("$baseUrl/$PREFIX_LIBRARY/$slug", tmoHeaders)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/library".toHttpUrl().newBuilder()
        url.addQueryParameter("title", query)
        val nsfwPart = getSFWUrlPart()
        if (nsfwPart.isNotEmpty()) {
            nsfwPart.split("&")
                .filter { it.isNotBlank() }
                .forEach { param ->
                    val (key, value) = param.split("=").let {
                        it.first().removePrefix("?") to it.getOrElse(1) { "" }
                    }
                    url.addQueryParameter(key, value)
                }
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
                        when (content.state) {
                            Filter.TriState.STATE_IGNORE -> url.addQueryParameter(content.id, "")
                            Filter.TriState.STATE_INCLUDE -> url.addQueryParameter(content.id, "true")
                            Filter.TriState.STATE_EXCLUDE -> url.addQueryParameter(content.id, "false")
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

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return safeClient.newCall(mangaDetailsRequest(manga))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response).apply { initialized = true }
            }
    }

    override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl + manga.url, tmoHeaders)

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select("h2.element-subtitle").text()
        document.select("h5.card-title").let {
            author = it.first()?.attr("title")?.substringAfter(", ")
            artist = it.last()?.attr("title")?.substringAfter(", ")
        }
        genre = buildList {
            addAll(document.select("a.py-2").eachText())
            document.selectFirst("h1.book-type")?.text()?.capitalize()?.also(::add)
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

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return safeClient.newCall(chapterListRequest(manga))
            .asObservableSuccess()
            .map { response ->
                chapterListParse(response)
            }
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

    private fun parseChapterDate(date: String): Long {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .parse(date)?.time ?: 0
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return safeClient.newCall(pageListRequest(chapter))
            .asObservableSuccess()
            .map { response ->
                pageListParse(response)
            }
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

    private fun getScanlatorPref(): Boolean = preferences.getBoolean(SCANLATOR_PREF, SCANLATOR_PREF_DEFAULT_VALUE)

    private fun getSaveLastCFUrlPref(): Boolean = preferences.getBoolean(SAVE_LAST_CF_URL_PREF, SAVE_LAST_CF_URL_PREF_DEFAULT_VALUE)

    private fun checkBox(
        ctx: Context,
        key: String,
        title: String,
        summary: String? = null,
        default: Boolean = false,
    ) = CheckBoxPreference(ctx).apply {
        this.key = key
        this.title = title
        summary?.let { this.summary = it }
        setDefaultValue(default)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val ctx = screen.context

        val nsfwGeneral = checkBox(
            ctx,
            SFW_GENERAL,
            "Ocultar todo el contenido NSFW",
            "Bloquea automáticamente Ecchi, GL, BL, Harem y Trap",
        )

        val ecchi = checkBox(ctx, NSFW_ECCHI, "    • Ocultar Ecchi")
        val gl = checkBox(ctx, NSFW_GIRLS_LOVE, "    • Ocultar Girls Love")
        val bl = checkBox(ctx, NSFW_BOYS_LOVE, "    • Ocultar Boys Love")
        val harem = checkBox(ctx, NSFW_HAREM, "    • Ocultar Harem")
        val trap = checkBox(ctx, NSFW_TRAP, "    • Ocultar Trap")

        val nsfwPrefs = listOf(ecchi, gl, bl, harem, trap)

        fun updateState(allSfwEnabled: Boolean) {
            val enabled = !allSfwEnabled

            nsfwPrefs.forEach { it.setEnabled(enabled) }

            if (allSfwEnabled && preferences.getString(NSFW_STATE_CACHE, null) == null) {
                cacheNsfwState()
                preferences.edit()
                    .putBoolean(NSFW_ECCHI, false)
                    .putBoolean(NSFW_GIRLS_LOVE, false)
                    .putBoolean(NSFW_BOYS_LOVE, false)
                    .putBoolean(NSFW_HAREM, false)
                    .putBoolean(NSFW_TRAP, false)
                    .apply()
            } else {
                restoreNsfwState()
            }
        }

        updateState(isSfwEnabled())

        nsfwGeneral.setOnPreferenceChangeListener { _, newValue ->
            updateState(newValue as Boolean)
            true
        }

        val scanlatorPref = checkBox(
            ctx,
            SCANLATOR_PREF,
            SCANLATOR_PREF_TITLE,
            SCANLATOR_PREF_SUMMARY,
            SCANLATOR_PREF_DEFAULT_VALUE,
        )

        val saveLastCFUrlPreference = checkBox(
            ctx,
            SAVE_LAST_CF_URL_PREF,
            SAVE_LAST_CF_URL_PREF_TITLE,
            SAVE_LAST_CF_URL_PREF_SUMMARY,
            SAVE_LAST_CF_URL_PREF_DEFAULT_VALUE,
        )

        screen.addPreference(nsfwGeneral)
        nsfwPrefs.forEach(screen::addPreference)
        screen.addPreference(scanlatorPref)
        screen.addPreference(saveLastCFUrlPreference)
    }

    private fun cacheNsfwState() {
        val state = NsfwState(
            ecchi = getNsfwEcchi(),
            girlsLove = getNsfwGirlsLove(),
            boysLove = getNsfwBoysLove(),
            harem = getNsfwHarem(),
            trap = getNsfwTrap(),
        )
        preferences.edit()
            .putString(NSFW_STATE_CACHE, Json.encodeToString(state))
            .apply()
    }

    private fun restoreNsfwState() {
        val json = preferences.getString(NSFW_STATE_CACHE, null) ?: return
        val state = runCatching {
            Json.decodeFromString<NsfwState>(json)
        }.getOrNull() ?: return

        preferences.edit()
            .putBoolean(NSFW_ECCHI, state.ecchi)
            .putBoolean(NSFW_GIRLS_LOVE, state.girlsLove)
            .putBoolean(NSFW_BOYS_LOVE, state.boysLove)
            .putBoolean(NSFW_HAREM, state.harem)
            .putBoolean(NSFW_TRAP, state.trap)
            .remove(NSFW_STATE_CACHE)
            .apply()
    }

    private fun isSfwEnabled(): Boolean =
        getSfwGeneral()

    private fun getSfwGeneral(): Boolean =
        preferences.getBoolean(SFW_GENERAL, false)

    private fun getNsfwEcchi(): Boolean =
        preferences.getBoolean(NSFW_ECCHI, false)

    private fun getNsfwGirlsLove(): Boolean =
        preferences.getBoolean(NSFW_GIRLS_LOVE, false)

    private fun getNsfwBoysLove(): Boolean =
        preferences.getBoolean(NSFW_BOYS_LOVE, false)

    private fun getNsfwHarem(): Boolean =
        preferences.getBoolean(NSFW_HAREM, false)

    private fun getNsfwTrap(): Boolean =
        preferences.getBoolean(NSFW_TRAP, false)

    companion object {
        val DIRPATH_REGEX = """var\s+dirPath\s*=\s*'(.*?)'\s*;""".toRegex()
        val IMAGES_REGEX = """var\s+images\s*=.*\[(.*?)\]\s*'\s*\)\s*;""".toRegex()

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

        private const val NSFW_STATE_CACHE = "pref_nsfw_state_cache"

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
        private val CF_SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
    }
}
