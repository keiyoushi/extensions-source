package eu.kanade.tachiyomi.extension.es.tmohentai

import android.app.Application
import android.content.SharedPreferences
import android.webkit.WebSettings
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferencesLazy
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TMOHentai :
    HttpSource(),
    ConfigurableSource {

    override val name = "TMOHentai"

    override val baseUrl = "https://tmohentai.app"

    override val lang = "es"

    override val supportsLatest = true

    // Use the WebView's native UA so that the cf_clearance cookie issued during
    // the WebView Cloudflare solve stays valid (Cloudflare binds the cookie to the
    // exact User-Agent that solved the challenge).
    private val webViewUserAgent: String? by lazy {
        runCatching { WebSettings.getDefaultUserAgent(Injekt.get<Application>()) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(::cloudflareInterceptor)
        .rateLimit(10)
        .build()

    /**
     * On 403 + cf-ray header (=Cloudflare challenge), load the base URL in a hidden WebView
     * so the Cloudflare Turnstile can auto-solve and set cf_clearance in the shared
     * CookieManager. The next OkHttp call will carry that cookie automatically.
     */
    private fun cloudflareInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code == 403 && response.header("cf-ray") != null) {
            response.close()
            // Resolve against the base URL; cf_clearance covers the whole domain.
            CloudflareResolver.resolve(
                loadUrl = baseUrl,
                cookieUrl = baseUrl,
                userAgent = webViewUserAgent,
            )
            return chain.proceed(request)
        }

        return response
    }

    override fun headersBuilder() = super.headersBuilder().apply {
        add("Referer", "$baseUrl/")
        add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        add("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
        // Sync UA with the WebView one (set lazily; no-op if null since addHeader ignores blank)
        webViewUserAgent?.let { set("User-Agent", it) }
    }

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun popularMangaRequest(page: Int): Request = if (page == 1) {
        GET(baseUrl, headers)
    } else {
        GET("$baseUrl/biblioteca?order_item=likes_count&order_dir=desc&page=$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val url = response.request.url

        if (url.toString() == "$baseUrl/" || url.toString() == baseUrl) {
            val mangas = document.select("#top_today a.manga-card").map { popularMangaFromElement(it) }
            return MangasPage(mangas, false)
        }

        val mangas = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }
        val hasNextPage = document.select(".pagination a[rel=next], .pagination a:contains(»), a.page-link:contains(»)").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    private fun popularMangaSelector() = "a.manga-card"

    private fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.attr("title").ifEmpty {
            element.select("h3.manga-card__title, h3").text()
        }.trim()
        val img = element.select("img.manga-card__cover, img")
        thumbnail_url = img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
        setUrlWithoutDomain(element.attr("href"))
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    private fun popularMangaNextPageSelector() = "a[rel=next]"

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/biblioteca?order_item=creation&order_dir=desc&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    private fun latestUpdatesSelector() = popularMangaSelector()

    private fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    private fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(response: Response): SManga = mangaDetailsParse(response.asJsoup())

    private fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select("#md-title").text()
        thumbnail_url = document.select(".md-cover-card__image-wrap img").attr("abs:src")

        val authorText = document.select(".md-badge--author, .label.md-badge--author").text()
        author = authorText.ifEmpty { "Desconocido" }
        artist = authorText.ifEmpty { "Desconocido" }

        description = document.select(".md-info-row--synopsis .md-info-row__value, .md-info-row--synopsis p").text().ifEmpty { "Sin descripción" }

        val statusText = document.select(".md-cover-card__status, .md-cover-card__status .md-badge").text()
        status = when {
            statusText.contains("Ongoing", ignoreCase = true) || statusText.contains("Publicándose", ignoreCase = true) -> SManga.ONGOING
            statusText.contains("Completed", ignoreCase = true) || statusText.contains("Finalizado", ignoreCase = true) -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        genre = document.select("#md-tags-list a").joinToString { it.text().trim() }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val readBtn = document.selectFirst(".md-preview-read-btn, .md-preview-card__header a")
            ?: return emptyList()
        val chapter = SChapter.create().apply {
            name = "Capítulo 1"
            setUrlWithoutDomain(readBtn.attr("href"))
        }
        return listOf(chapter)
    }

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val images = document.select(".reader-img-wrap img, .reader-img-wrap source, img.reader-img, source.reader-img")
        return images.mapIndexedNotNull { index, element ->
            val imageUrl = element.extractImageUrl()
            if (imageUrl == null) {
                null
            } else {
                Page(index, "", imageUrl)
            }
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val searchQuery = query.trim()
        var isTopRange = false
        var topRangeVal = ""

        val filterList = if (filters.isEmpty()) getFilterList() else filters

        filterList.forEach { filter ->
            if (filter is PopularRangeFilter) {
                val value = filter.toUriPart()
                if (value.isNotEmpty()) {
                    isTopRange = true
                    topRangeVal = value
                }
            }
        }

        if (isTopRange && searchQuery.isEmpty()) {
            return GET("$baseUrl/?range=$topRangeVal", headers)
        }

        // Collect selected genres and content type to decide which endpoint to use.
        val selectedGenres = mutableListOf<String>()
        var contentType = ""
        var sortItem = ""
        var sortDir = ""

        filterList.forEach { filter ->
            when (filter) {
                is ContentType -> contentType = filter.toUriPart()
                is GenreList -> filter.state.filter { it.state }.forEach { selectedGenres.add(it.id) }
                is SortBy -> {
                    if (filter.state != null) {
                        sortItem = SORTABLES[filter.state!!.index].second
                        sortDir = if (filter.state!!.ascending) "asc" else "desc"
                    }
                }
                else -> {}
            }
        }

        val hasTagsOrQuery = searchQuery.isNotEmpty() || selectedGenres.isNotEmpty() || contentType.isNotEmpty()

        return if (hasTagsOrQuery) {
            // Root URL is the correct endpoint for text/tag/content searches and supports pagination.
            // e.g. https://tmohentai.app/?title=X&content=Y&tags[]=Z&page=P
            val url = baseUrl.toHttpUrl().newBuilder()
            url.addQueryParameter("title", searchQuery)
            url.addQueryParameter("content", contentType)
            selectedGenres.forEach { id -> url.addQueryParameter("tags[]", id) }
            url.addQueryParameter("page", page.toString())
            GET(url.build(), headers)
        } else {
            // /biblioteca is for sort-only browsing (no tag/text filter).
            val url = "$baseUrl/biblioteca".toHttpUrl().newBuilder()
            url.addQueryParameter("page", page.toString())
            if (sortItem.isNotEmpty()) {
                url.addQueryParameter("order_item", sortItem)
                url.addQueryParameter("order_dir", sortDir)
            }
            GET(url.build(), headers)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val url = response.request.url

        // Popular range (top_today / top_weekly / top_monthly) — no pagination.
        if (url.queryParameter("range") != null) {
            val range = url.queryParameter("range") ?: "today"
            val containerId = when (range) {
                "today" -> "#top_today"
                "weekly" -> "#top_weekly"
                "monthly" -> "#top_monthly"
                else -> "#top_today"
            }
            val mangas = document.select("$containerId a.manga-card").map { popularMangaFromElement(it) }
            return MangasPage(mangas, false)
        }

        val mangas = document.select("a.manga-card").map { popularMangaFromElement(it) }
        val hasNextPage = document.selectFirst(".pagination a[rel=next], .pagination a:contains(»), a.page-link:contains(»)") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun searchMangaSelector() = popularMangaSelector()

    private fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    private fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    private fun searchMangaByIdRequest(id: String) = GET("$baseUrl/view_uploads/$id", headers)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = if (query.startsWith(PREFIX_ID_SEARCH)) {
        val realQuery = query.removePrefix(PREFIX_ID_SEARCH)

        client.newCall(searchMangaByIdRequest(realQuery))
            .asObservableSuccess()
            .map { response ->
                val document = response.asJsoup()
                val detailsLink = document.select("a[href*=library/]").firstOrNull()
                    ?: throw Exception("Detalle no encontrado en la página del lector")
                val detailsUrl = detailsLink.attr("href")

                val detailsResponse = client.newCall(GET(baseUrl + detailsUrl, headers)).execute()
                val details = mangaDetailsParse(detailsResponse)
                details.url = detailsUrl
                MangasPage(listOf(details), false)
            }
    } else {
        client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                searchMangaParse(response)
            }
    }

    private class Genre(name: String, val id: String) : Filter.CheckBox(name)

    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Géneros", genres)

    override fun getFilterList() = FilterList(
        PopularRangeFilter(),
        Filter.Separator(),
        ContentType(),
        Filter.Separator(),
        SortBy(),
        Filter.Separator(),
        GenreList(getGenreList()),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private class PopularRangeFilter :
        UriPartFilter(
            "Rango de populares (Solo portada)",
            arrayOf(
                Pair("Todo (Biblioteca)", ""),
                Pair("Top Hoy", "today"),
                Pair("Top Semanal (7 días)", "weekly"),
                Pair("Top Mensual (30 días)", "monthly"),
            ),
        )

    private class ContentType :
        UriPartFilter(
            "Categoría de contenido",
            arrayOf(
                Pair("Todos", ""),
                Pair("Yaoi ♂♂", "yaoi"),
                Pair("Yuri ♀♀", "yuri"),
                Pair("Futanari ⚧", "futanari"),
                Pair("Solo Femenino ♀", "sole-female"),
                Pair("Solo Masculino ♂", "sole-male"),
                Pair("Vanilla ♡", "vanilla"),
                Pair("NTR / Netorare", "ntr"),
                Pair("Uncensored", "uncensored"),
            ),
        )

    class SortBy :
        Filter.Sort(
            "Ordenar por",
            SORTABLES.map { it.first }.toTypedArray(),
            Selection(0, false),
        )

    private fun getGenreList() = listOf(
        Genre("2b", "2108"),
        Genre("3d", "68"),
        Genre("adultery", "6"),
        Genre("afro", "1633"),
        Genre("age progression", "424"),
        Genre("agnes tachyon", "508"),
        Genre("ahegao", "7"),
        Genre("alice margatroid", "1422"),
        Genre("amputee", "1003"),
        Genre("anal", "16"),
        Genre("anal intercourse", "159"),
        Genre("android 18", "498"),
        Genre("android 21", "1216"),
        Genre("apron", "372"),
        Genre("asphyxiation", "258"),
        Genre("aunt", "252"),
        Genre("bald", "161"),
        Genre("ball sucking", "415"),
        Genre("bbm", "162"),
        Genre("bbw", "23"),
        Genre("bdsm", "421"),
        Genre("beauty mark", "123"),
        Genre("bestiality", "51"),
        Genre("big areolae", "109"),
        Genre("big ass", "27"),
        Genre("big balls", "471"),
        Genre("big boobs", "28"),
        Genre("big breast", "75"),
        Genre("big breasts", "5"),
        Genre("big clit", "510"),
        Genre("big lips", "708"),
        Genre("big muscles", "676"),
        Genre("big nipples", "292"),
        Genre("big penis", "156"),
        Genre("big vagina", "762"),
        Genre("bikini", "110"),
        Genre("bisexual", "72"),
        Genre("blackmail", "253"),
        Genre("blood", "245"),
        Genre("bloomers", "189"),
        Genre("blowjob", "8"),
        Genre("blowjob face", "201"),
        Genre("body modification", "591"),
        Genre("bodysuit", "395"),
        Genre("bondage", "60"),
        Genre("breast feeding", "118"),
        Genre("bride", "163"),
        Genre("brother", "474"),
        Genre("bukkake", "52"),
        Genre("bulma briefs", "485"),
        Genre("bunny boy", "677"),
        Genre("caelus", "259"),
        Genre("catboy", "445"),
        Genre("catgirl", "84"),
        Genre("cheating", "9"),
        Genre("cheerleader", "318"),
        Genre("chi chi", "613"),
        Genre("chikan", "190"),
        Genre("chinese dress", "333"),
        Genre("chloe", "2054"),
        Genre("chloroform", "522"),
        Genre("clit stimulation", "254"),
        Genre("clothed female nude male", "523"),
        Genre("clothed paizuri", "689"),
        Genre("collar", "165"),
        Genre("colour", "31"),
        Genre("comedy", "71"),
        Genre("comic", "148"),
        Genre("condom", "119"),
        Genre("corset", "800"),
        Genre("cosplaying", "284"),
        Genre("cousin", "423"),
        Genre("cowgirl", "234"),
        Genre("creampie", "38"),
        Genre("crossdressing", "215"),
        Genre("crotch tattoo", "379"),
        Genre("cum bath", "1035"),
        Genre("cunnilingus", "136"),
        Genre("curren chan", "943"),
        Genre("daiwa scarlet", "992"),
        Genre("dark skin", "29"),
        Genre("darkness", "1562"),
        Genre("daughter", "166"),
        Genre("deepthroat", "42"),
        Genre("defloration", "101"),
        Genre("demon", "346"),
        Genre("demon girl", "277"),
        Genre("dickgirl on dickgirl", "285"),
        Genre("dickgirl on female", "209"),
        Genre("dickgirl on male", "265"),
        Genre("digital", "85"),
        Genre("dilf", "124"),
        Genre("doctor", "1161"),
        Genre("dog", "151"),
        Genre("dog boy", "679"),
        Genre("domination", "30"),
        Genre("domination loss", "78"),
        Genre("double anal", "1032"),
        Genre("double blowjob", "600"),
        Genre("double penetration", "49"),
        Genre("drill hair", "289"),
        Genre("drugs", "183"),
        Genre("drunk", "184"),
        Genre("elf", "220"),
        Genre("ellen joe", "1160"),
        Genre("emotionless sex", "175"),
        Genre("exhibitionism", "34"),
        Genre("exposed clothing", "359"),
        Genre("eye-covering bang", "167"),
        Genre("facial hair", "221"),
        Genre("fantasy", "50"),
        Genre("farting", "142"),
        Genre("females only", "137"),
        Genre("femdom", "59"),
        Genre("feminization", "204"),
        Genre("fetish", "61"),
        Genre("fff threesome", "138"),
        Genre("ffm threesome", "46"),
        Genre("filming", "65"),
        Genre("fingering", "139"),
        Genre("firefly", "297"),
        Genre("first person perspective", "356"),
        Genre("focus anal", "104"),
        Genre("focus blowjob", "357"),
        Genre("foot licking", "86"),
        Genre("footjob", "76"),
        Genre("forced", "39"),
        Genre("fox girl", "477"),
        Genre("full censorship", "177"),
        Genre("full color", "132"),
        Genre("full-packaged futanari", "210"),
        Genre("furry", "32"),
        Genre("futa", "19"),
        Genre("futanari", "44"),
        Genre("gag", "683"),
        Genre("gang rape", "364"),
        Genre("garter belt", "509"),
        Genre("gender change", "129"),
        Genre("gender morph", "130"),
        Genre("ghost", "295"),
        Genre("glasses", "111"),
        Genre("gloves", "106"),
        Genre("goblin", "398"),
        Genre("grandmother", "654"),
        Genre("group", "26"),
        Genre("growth", "571"),
        Genre("gyaru", "45"),
        Genre("gyaru-oh", "131"),
        Genre("hairy", "120"),
        Genre("hairy armpits", "168"),
        Genre("handjob", "321"),
        Genre("harem", "13"),
        Genre("headphones", "1300"),
        Genre("hero", "1306"),
        Genre("hestia", "2672"),
        Genre("hidden sex", "152"),
        Genre("hinata hyuga", "729"),
        Genre("hood", "389"),
        Genre("horns", "240"),
        Genre("horror", "62"),
        Genre("horse", "504"),
        Genre("horse girl", "316"),
        Genre("hotpants", "480"),
        Genre("huge breasts", "133"),
        Genre("huge penis", "426"),
        Genre("human cattle", "430"),
        Genre("human on furry", "197"),
        Genre("humiliation", "36"),
        Genre("hypno", "1649"),
        Genre("ia", "2745"),
        Genre("idoll sun", "1481"),
        Genre("impregnation", "3"),
        Genre("incest", "12"),
        Genre("inflation", "222"),
        Genre("inverted nipples", "290"),
        Genre("kanojo", "2701"),
        Genre("kemonomimi", "88"),
        Genre("kimono", "616"),
        Genre("kissing", "43"),
        Genre("kitasan black", "937"),
        Genre("knotted penis", "1787"),
        Genre("kunoichi", "489"),
        Genre("lactation", "263"),
        Genre("large tattoo", "112"),
        Genre("leash", "235"),
        Genre("leg lock", "121"),
        Genre("lingerie", "89"),
        Genre("loli", "57"),
        Genre("long tongue", "231"),
        Genre("low smegma", "1669"),
        Genre("magical girl", "377"),
        Genre("maid", "301"),
        Genre("male on dickgirl", "376"),
        Genre("males only", "353"),
        Genre("masked face", "446"),
        Genre("masturbation", "125"),
        Genre("mature", "10"),
        Genre("mesuiki", "371"),
        Genre("midget", "487"),
        Genre("milf", "2"),
        Genre("military", "363"),
        Genre("milking", "431"),
        Genre("mind break", "20"),
        Genre("mind control", "98"),
        Genre("mmf threesome", "47"),
        Genre("monkey", "452"),
        Genre("monster", "560"),
        Genre("monster girl", "232"),
        Genre("monsters", "35"),
        Genre("moral degeneration", "549"),
        Genre("mosaic censorship", "126"),
        Genre("mother", "54"),
        Genre("mouth mask", "256"),
        Genre("multi-work series", "90"),
        Genre("multiple orgasms", "247"),
        Genre("multiple paizuri", "416"),
        Genre("muscle", "113"),
        Genre("nakadashi", "91"),
        Genre("naruto uzumaki", "730"),
        Genre("netorare", "4"),
        Genre("netorase", "73"),
        Genre("niece", "481"),
        Genre("nipple piercing", "589"),
        Genre("nipple stimulation", "255"),
        Genre("no penetration", "143"),
        Genre("ntr", "1"),
        Genre("nun", "320"),
        Genre("nurse", "344"),
        Genre("nympho", "40"),
        Genre("old man", "223"),
        Genre("onahole", "466"),
        Genre("oni", "241"),
        Genre("onsen", "517"),
        Genre("oppai loli", "354"),
        Genre("orgy", "48"),
        Genre("oyakodon", "53"),
        Genre("painted nails", "266"),
        Genre("paizuri", "157"),
        Genre("pantyhose", "122"),
        Genre("parody", "66"),
        Genre("pasties", "417"),
        Genre("pegging", "673"),
        Genre("penis enlargement", "690"),
        Genre("phimosis", "303"),
        Genre("piercing", "512"),
        Genre("pig", "339"),
        Genre("piss drinking", "693"),
        Genre("policewoman", "700"),
        Genre("ponytail", "171"),
        Genre("possession", "224"),
        Genre("pregnant", "33"),
        Genre("prostitution", "200"),
        Genre("public use", "205"),
        Genre("ranma saotome", "328"),
        Genre("rape", "21"),
        Genre("rias gremory", "1131"),
        Genre("rider", "1491"),
        Genre("rimjob", "193"),
        Genre("romance", "64"),
        Genre("rough translation", "107"),
        Genre("ryona", "302"),
        Genre("scar", "559"),
        Genre("scat", "667"),
        Genre("school gym uniform", "191"),
        Genre("school swimsuit", "178"),
        Genre("schoolboy uniform", "182"),
        Genre("schoolgirl", "22"),
        Genre("schoolgirl uniform", "140"),
        Genre("sensei", "268"),
        Genre("sex toys", "114"),
        Genre("shared senses", "447"),
        Genre("shark girl", "1159"),
        Genre("sheep girl", "448"),
        Genre("shemale", "267"),
        Genre("shibari", "553"),
        Genre("shirou emiya", "988"),
        Genre("shota", "37"),
        Genre("sister", "150"),
        Genre("slave", "172"),
        Genre("sleeping", "92"),
        Genre("slime", "399"),
        Genre("small boobs", "67"),
        Genre("small breasts", "93"),
        Genre("small penis", "206"),
        Genre("smalldom", "227"),
        Genre("smegma", "482"),
        Genre("smell", "198"),
        Genre("snake", "2049"),
        Genre("sole dickgirl", "211"),
        Genre("sole female", "25"),
        Genre("sole male", "24"),
        Genre("solo action", "145"),
        Genre("son gohan", "648"),
        Genre("son goku", "337"),
        Genre("spanking", "752"),
        Genre("spider", "1141"),
        Genre("sport", "77"),
        Genre("squirting", "192"),
        Genre("stockings", "115"),
        Genre("stomach deformation", "94"),
        Genre("story arc", "116"),
        Genre("strap-on", "207"),
        Genre("student", "58"),
        Genre("sweating", "179"),
        Genre("swimsuit", "117"),
        Genre("tail", "272"),
        Genre("tail plug", "413"),
        Genre("tall girl", "56"),
        Genre("tall man", "236"),
        Genre("tankoubon", "276"),
        Genre("tanlines", "462"),
        Genre("teacher", "80"),
        Genre("tentacles", "14"),
        Genre("thick eyebrows", "202"),
        Genre("tomboy", "55"),
        Genre("tomgirl", "228"),
        Genre("toys", "41"),
        Genre("transformation", "280"),
        Genre("tribadism", "146"),
        Genre("tsunade", "595"),
        Genre("tsundere", "74"),
        Genre("twintails", "95"),
        Genre("uncensored", "63"),
        Genre("uncle", "2404"),
        Genre("unusual insertions", "1675"),
        Genre("unusual pupils", "153"),
        Genre("urination", "286"),
        Genre("vaginal birth", "194"),
        Genre("vanilla", "15"),
        Genre("variant set", "154"),
        Genre("very long hair", "96"),
        Genre("virgin", "69"),
        Genre("virginity", "81"),
        Genre("vivlos", "804"),
        Genre("vtuber", "366"),
        Genre("warrior", "400"),
        Genre("western cg", "1404"),
        Genre("wise", "216"),
        Genre("witch", "609"),
        Genre("wolf boy", "214"),
        Genre("wolf girl", "199"),
        Genre("x-ray", "97"),
        Genre("yandere", "70"),
        Genre("yaoi", "18"),
        Genre("yuri", "17"),
    )

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {}

    private fun Element.extractImageUrl(): String? {
        val candidates = listOf(
            attr("abs:data-src"),
            attr("abs:data-lazy-src"),
            attr("abs:data-original"),
            attr("abs:src"),
            attr("abs:data-srcset").firstUrlFromSrcSet(),
            attr("abs:data-lazy-srcset").firstUrlFromSrcSet(),
            attr("abs:srcset").firstUrlFromSrcSet(),
            attr("data-src").trim(),
            attr("data-lazy-src").trim(),
            attr("data-original").trim(),
            attr("src").trim(),
            attr("data-srcset").firstUrlFromSrcSet(),
            attr("data-lazy-srcset").firstUrlFromSrcSet(),
            attr("srcset").firstUrlFromSrcSet(),
        )

        candidates.firstOrNull { it.isNotBlank() && it.startsWith("http") && !it.startsWith("data:") }?.let { return it }

        return attr("style")
            .takeIf { it.isNotBlank() }
            ?.let { style -> BACKGROUND_IMAGE_URL_REGEX.find(style)?.groupValues?.getOrNull(1) }
    }

    private fun String.firstUrlFromSrcSet(): String = trim().split(",").firstOrNull()?.trim()?.split(" ")?.firstOrNull().orEmpty()

    companion object {
        const val PREFIX_ID_SEARCH = "id:"

        private val BACKGROUND_IMAGE_URL_REGEX = Regex("""background-image\s*:\s*url\(['\"]?([^'\")]+)['\"]?\)""")

        private val SORTABLES = listOf(
            Pair("Más populares", "likes_count"),
            Pair("Mejor valorados", "score"),
            Pair("Alfabético", "alphabetically"),
            Pair("Más recientes", "creation"),
            Pair("Fecha estreno", "release_date"),
        )
    }
}
