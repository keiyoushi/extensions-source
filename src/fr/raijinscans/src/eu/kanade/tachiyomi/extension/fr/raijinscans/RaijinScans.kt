package eu.kanade.tachiyomi.extension.fr.raijinscans

import android.content.SharedPreferences
import android.util.Base64
import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.util.Calendar
import java.util.Locale
import kotlin.getValue

class RaijinScans :
    HttpSource(),
    ConfigurableSource {

    override val name = "Raijin Scans"

    override val baseUrl = "https://raijin-scans.fr"

    override val lang = "fr"

    override val supportsLatest = true

    private val hasPremiumChapters = true

    private var nonce: String? = null

    private val preferences: SharedPreferences by getPreferencesLazy()
    private val nonceRegex = """"nonce"\s*:\s*"([^"]+)"""".toRegex()
    private val numberRegex = """(\d+)""".toRegex()
    private val descriptionScriptRegex = """content\.innerHTML = `([\s\S]+?)`;""".toRegex()
    private val manifestPushRegex = """push\((\{.*\})\);""".toRegex()
    private val json: Json by lazy {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    override fun headersBuilder() = super.headersBuilder().add("Referer", "$baseUrl/")

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("section#most-viewed div.swiper-slide.unit").map(::popularMangaFromElement)
        return MangasPage(mangas, false)
    }

    private fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val titleElement = element.selectFirst("a.c-title")!!
        setUrlWithoutDomain(titleElement.attr("abs:href"))
        title = titleElement.text()
        thumbnail_url = element.selectFirst("a.poster div.poster-image-wrapper > img")?.attr("abs:src")
    }

    // ================================ Recent ================================
    override fun latestUpdatesRequest(page: Int): Request {
        if (page == 1) {
            return GET(baseUrl, headers)
        }

        val currentNonce = nonce
            ?: throw Exception("Nonce not found. Please try refreshing by pulling down on the 'Recent' page.")

        val formBody = FormBody.Builder()
            .add("action", "load_manga")
            .add("page", (page - 1).toString())
            .add("nonce", currentNonce)
            .build()

        val xhrHeaders = headersBuilder()
            .set("X-Requested-With", "XMLHttpRequest")
            .set("Accept", "*/*")
            .add("Origin", baseUrl)
            .build()

        return POST("$baseUrl/wp-admin/admin-ajax.php", xhrHeaders, formBody)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        if (response.request.method == "GET") {
            val document = response.asJsoup()
            val scriptElement = document.selectFirst("script#ajax-sh-js-extra")?.data()
            nonce = scriptElement?.let { nonceRegex.find(it)?.groupValues?.get(1) }

            val mangas = document.select("section.recently-updated div.unit").map(::searchMangaFromElement)
            val hasNextPage = document.selectFirst("a#load-more-manga") != null
            return MangasPage(mangas, hasNextPage)
        }

        val data = response.parseAs<LatestUpdatesDto>()

        if (!data.success) {
            return MangasPage(emptyList(), false)
        }

        val documentFragment = Jsoup.parseBodyFragment(data.data.mangaHtml, baseUrl)
        val mangas = documentFragment.select("div.unit").map(::searchMangaFromElement)
        val hasNextPage = data.data.currentPage < data.data.totalPages

        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Search ==============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (page > 1) addPathSegment("page").addPathSegment(page.toString())
            addQueryParameter("s", query)
            addQueryParameter("post_type", "wp-manga")
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.original.card-lg div.unit").map(::searchMangaFromElement)
        val hasNextPage = document.selectFirst("li.page-item:not(.disabled) a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        val linkElement = element.selectFirst("div.info > a")!!
        setUrlWithoutDomain(linkElement.attr("abs:href"))
        title = linkElement.text()
        thumbnail_url = element.selectFirst("div.poster-image-wrapper > img")?.attr("abs:src")
    }

    // =========================== Manga Details ===========================
    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1.serie-title")!!.text()
        author = document.selectFirst("div.stat-item:has(span:contains(Auteur)) span.stat-value")?.text()
        artist = document.selectFirst("div.stat-item:has(span:contains(Artiste)) span.stat-value")?.text()

        val scriptDescription = document.select("script:containsData(content.innerHTML)")
            .firstNotNullOfOrNull { descriptionScriptRegex.find(it.data())?.groupValues?.get(1)?.trim() }
        description = scriptDescription ?: document.selectFirst("div.description-content")?.text()

        genre = document.select("div.genre-list div.genre-link").joinToString { it.text() }

        thumbnail_url = document.selectFirst("img.cover")?.attr("abs:src")
        status = parseStatus(document.selectFirst("div.stat-item:has(span:contains(État du titre)) span.manga")?.text())
    }

    override fun mangaDetailsParse(response: Response): SManga = mangaDetailsParse(response.asJsoup())

    private fun parseStatus(status: String?): Int {
        val lcStatus = status?.lowercase() ?: return SManga.UNKNOWN
        return when {
            "en cours" in lcStatus -> SManga.ONGOING
            "terminé" in lcStatus -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // ========================= Chapter List ==========================
    override fun chapterListParse(response: Response): List<SChapter> {
        val showPremium = !hasPremiumChapters || preferences.getBoolean(
            SHOW_PREMIUM_KEY,
            SHOW_PREMIUM_DEFAULT,
        )
        val elements = response
            .asJsoup()
            .select("ul.scroll-sm li.item")
            .filterNot { !showPremium && it.selectFirst("a.cairo-premium") != null }
        return elements.map { e ->
            chapterFromElement(e, response.request.url.toString())
        }
    }

    private fun chapterFromElement(element: Element, mangaUrl: String): SChapter = SChapter.create().apply {
        val premium = hasPremiumChapters && element.selectFirst("a.cairo-premium") != null
        val link = element.selectFirst("a")!!
        val title = link.attr("title")
        val endUrl: String
        if (!link.toString().contains("connexion")) {
            endUrl = link.attr("abs:href")
        } else {
            val titleSplit = title.split(" ")
            endUrl = URI(mangaUrl).resolve(titleSplit[1]).toString()
        }
        setUrlWithoutDomain(endUrl)
        name = buildString {
            if (premium) append("🔒 ")
            append(title)
        }
        date_upload = parseRelativeDateString(link.selectFirst("> span:nth-of-type(2)")?.text())
    }

    private fun parseRelativeDateString(date: String?): Long {
        if (date == null) return 0L

        val lcDate = date.lowercase(Locale.FRENCH).trim()
        val cal = Calendar.getInstance()
        val number = numberRegex.find(lcDate)?.value?.toIntOrNull()

        return when {
            "aujourd'hui" in lcDate -> cal.timeInMillis

            "hier" in lcDate -> cal.apply { add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis

            number != null -> when {
                ("h" in lcDate || "heure" in lcDate) && "chapitre" !in lcDate -> cal.apply { add(Calendar.HOUR_OF_DAY, -number) }.timeInMillis
                "min" in lcDate -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
                "jour" in lcDate || lcDate.endsWith("j") -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
                "semaine" in lcDate -> cal.apply { add(Calendar.WEEK_OF_YEAR, -number) }.timeInMillis
                "mois" in lcDate || (lcDate.endsWith("m") && "min" !in lcDate) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
                "an" in lcDate -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
                else -> 0L
            }

            else -> 0L
        }
    }

    // ========================== Page List =============================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        // left connexion check just in case cause url returned by the website is /connexion but then it wont show in chapters list
        // so i made it so it goes to /{mangaurl}/{chapter number} which should show in almost all case the buy premium page and if it doesnt whatever it throw a 404
        val isPremium = document.select(".subscription-required-message").isNotEmpty() || response.request.url.toString().contains("connexion")
        if (isPremium) {
            throw Exception("This chapter is premium. Please connect via the webview to view.")
        }

        val chapterUrl = response.request.url.toString()
        val ajaxHeaders = headersBuilder()
            .set("Referer", chapterUrl)
            .set("X-Requested-With", "XMLHttpRequest")
            .set("Accept", "*/*")
            .add("Origin", baseUrl)
            .build()

        // window["rjfr_xxx"].push({
        //   "m": "hex1|...",  // order of fragments
        //   "c": {               // key-value pairs of b64 fragments
        //     "hex1": "base64fragment1", ... }
        // })
        // config = m.split("|").map { key -> c[key] }.joinToString("") -> Base64 decode

        val manifestScript = document.select("script").find { it.data().contains("rjfr_") }
            ?: throw Exception("No reader manifest found. Open the chapter in WebView.")
        val scriptData = manifestScript.data()
        val match = manifestPushRegex.find(scriptData) ?: throw Exception("Invalid manifest format")

        val manifestJson = match.groupValues[1].parseAs<JsonObject>()
        val mOrder = manifestJson["m"]!!.jsonPrimitive.content.split("|")
        val cObj = manifestJson["c"]!!.jsonObject
        val fragments = mOrder.map { cObj[it]!!.jsonPrimitive.content }
        val b64 = fragments.joinToString("")

        val config = String(Base64.decode(b64, Base64.DEFAULT)).parseAs<JsonObject>()
        val shuffled = config["d"]!!.jsonArray
        val perm = config["m"]!!.jsonArray.map { it.jsonPrimitive.int }

        // The config is shuffled: 'm' is a permutation that descrambles 'd' into its
        // canonical layout via ordered[m[i]] = d[i] (the same logic the reader JS uses).
        // Reading fixed positions of the descrambled array is robust to the two key arrays
        // changing length (which previously broke a size-based heuristic and caused HTTP 400).
        val ordered = arrayOfNulls<JsonElement>(shuffled.size)
        perm.forEachIndexed { i, p -> ordered[p] = shuffled[i] }
        fun at(index: Int): JsonElement = ordered.getOrNull(index)
            ?: throw Exception("Reader manifest layout changed. Open the chapter in WebView.")

        /* Canonical layout of the descrambled array:
         *  2  : Token (64 hex chars)
         *  3  : Instance ID
         *  4  : Manga ID
         *  5  : Chapter number/slug (== last URL segment)
         *  7  : Root ("rjfr-<mangaId>-<chapterId>", also in the DOM)
         * 12  : Form action ("rjfr_...")
         * 13  : reqKeys  - request form field names
         * 14  : respKeys - response field names
         * others (0,1,6,8,9,10,11) are the ajax url / constants / ignored values.
         */
        val token = at(2).jsonPrimitive.content
        val instanceId = at(3).jsonPrimitive.content
        val mangaId = at(4).jsonPrimitive.content
        val action = at(12).jsonPrimitive.content
        val reqKeys = at(13).jsonArray.map { it.jsonPrimitive.content }
        val respKeys = at(14).jsonArray.map { it.jsonPrimitive.content }

        val chapterSlug = response.request.url.pathSegments.last { it.isNotEmpty() }
        val rjfrValue = document.select("[data-rj-free-reader-root]").attr("data-rj-free-reader-root")

        val pages = mutableListOf<Page>()
        var offset = "0"
        var cursor = ""
        var run = true
        var guard = 0

        while (run && guard++ < MAX_PAGE_REQUESTS) {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("action", action)
                .addFormDataPart(reqKeys[0], "")
                .addFormDataPart(reqKeys[1], token)
                .addFormDataPart(reqKeys[2], instanceId)
                .addFormDataPart(reqKeys[3], mangaId)
                .addFormDataPart(reqKeys[4], chapterSlug)
                .addFormDataPart(reqKeys[5], "local")
                .addFormDataPart(reqKeys[6], "0")
                .addFormDataPart(reqKeys[7], offset)
                .addFormDataPart(reqKeys[8], rjfrValue)
                .addFormDataPart(reqKeys[9], cursor)
                .build()

            val response = client.newCall(POST("$baseUrl/wp-admin/admin-ajax.php", ajaxHeaders, body)).execute()

            if (!response.isSuccessful) error("Failed to get page: ${response.code}")
            val root = response.parseAs<JsonObject>()

            val payload = root[respKeys[1]]!!.jsonObject
            val images = payload[respKeys[2]]!!.jsonArray

            images.forEach { image ->
                val url = image.jsonObject[respKeys[4]]!!.jsonPrimitive.content
                pages.add(Page(pages.size, imageUrl = url))
            }

            offset = payload[respKeys[7]]?.jsonPrimitive?.content ?: ""
            cursor = payload[respKeys[8]]?.jsonPrimitive?.content ?: ""
            run = payload[respKeys[9]]?.jsonPrimitive?.boolean ?: false
        }

        return pages
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used.")

    // ========================== Preference =============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        if (hasPremiumChapters) {
            CheckBoxPreference(screen.context).apply {
                key = SHOW_PREMIUM_KEY
                title = "Show premium chapters"
                summary = "Show paid chapters (identified by 🔒) in the list."
                setDefaultValue(SHOW_PREMIUM_DEFAULT)
            }.also(screen::addPreference)
        }
    }

    companion object {
        private const val SHOW_PREMIUM_KEY = "show_premium_chapters"
        private const val SHOW_PREMIUM_DEFAULT = false
        private const val MAX_PAGE_REQUESTS = 100
    }
}
