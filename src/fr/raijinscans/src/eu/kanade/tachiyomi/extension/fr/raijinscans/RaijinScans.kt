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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
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
    private val manifestObjectRegex = """(\{"m":"[\s\S]*\})""".toRegex()
    private val imageExtRegex = """\.(webp|jpe?g|png|gif|avif)""".toRegex(RegexOption.IGNORE_CASE)
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

        // The chapter HTML injects a manifest object into a window["rjfr_xxx"] array. The
        // injection syntax has varied over time (e.g. `.push({...})` and `[…length] = {...}`),
        // so we extract the object literal directly rather than depend on the surrounding code:
        //   {
        //     "m": "hex1|...",  // order of fragments
        //     "c": {               // key-value pairs of b64 fragments
        //       "hex1": "base64fragment1", ... }
        //   }
        // config = m.split("|").map { key -> c[key] }.joinToString("") -> Base64 decode

        val manifestScript = document.select("script").find { it.data().contains("rjfr_") }
            ?: throw Exception("No reader manifest found. Open the chapter in WebView.")
        val scriptData = manifestScript.data()
        val match = manifestObjectRegex.find(scriptData) ?: throw Exception("Invalid manifest format")

        val manifestJson = match.groupValues[1].parseAs<JsonObject>()
        val mOrder = manifestJson["m"]!!.jsonPrimitive.content.split("|")
        val cObj = manifestJson["c"]!!.jsonObject
        val fragments = mOrder.map { cObj[it]!!.jsonPrimitive.content }
        val b64 = fragments.joinToString("")

        val config = String(Base64.decode(b64, Base64.DEFAULT)).parseAs<JsonObject>()
        val shuffled = config["d"]!!.jsonArray
        val perm = config["m"]!!.jsonArray.map { it.jsonPrimitive.int }
        val order = config["l"]!!.jsonArray.map { it.jsonPrimitive.int }

        // The reader uses two permutations to assemble the request. 'm' descrambles 'd' into
        // an intermediate array via ordered[m[i]] = d[i]; 'l' then maps that into the canonical
        // request layout via vals[i] = ordered[l[i]]. The manifest is randomized on every page
        // load (arrays/values land at different positions each time), so reading fixed positions
        // of the descrambled array is NOT reliable — both permutations must be applied.
        val ordered = arrayOfNulls<JsonElement>(shuffled.size)
        perm.forEachIndexed { i, p -> ordered[p] = shuffled[i] }
        val vals = order.map {
            ordered.getOrNull(it)
                ?: throw Exception("Reader manifest layout changed. Open the chapter in WebView.")
        }

        // Canonical layout of vals:
        //  1..6 : request content values, passed positionally to keyArr[0..5]
        //         (["", token, X, mangaId, chapterSlug, instanceId] — order matters, identity does not)
        //  13   : keyArr - request form field names (first 10 used)
        // The form action ("rjfr_...") is picked by content: it is the only string starting with
        // "rjfr_" (the rjfr instance token uses a hyphen, "rjfr-", so it won't collide).
        val action = vals.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .first { it.startsWith("rjfr_") }
        val keyArr = vals[13].jsonArray.map { it.jsonPrimitive.content }
        val contentValues = (1..6).map { vals[it].jsonPrimitive.content }

        val rjfrValue = document.select("[data-rj-free-reader-root]").attr("data-rj-free-reader-root")

        val pages = mutableListOf<Page>()
        var cursor = ""
        var run = true
        var guard = 0

        while (run && guard++ < MAX_PAGE_REQUESTS) {
            val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("action", action)
            contentValues.forEachIndexed { j, v -> builder.addFormDataPart(keyArr[j], v) }
            builder.addFormDataPart(keyArr[6], "0")
                .addFormDataPart(keyArr[7], "0") // offset, always 0; pagination is cursor-only
                .addFormDataPart(keyArr[8], rjfrValue)
                .addFormDataPart(keyArr[9], cursor)
            val body = builder.build()

            val response = client.newCall(POST("$baseUrl/wp-admin/admin-ajax.php", ajaxHeaders, body)).execute()

            if (!response.isSuccessful) error("Failed to get page: ${response.code}")
            val root = response.parseAs<JsonObject>()

            // Parse by content, not position, so we don't depend on the (randomized) response key
            // names, the response nesting depth, or decoy wrapper objects/arrays the site mixes in:
            // images = the array of image objects found anywhere in the tree (an image object holds
            // an http string whose path is an actual image); payload = its parent object, which also
            // carries the cursor (its only string primitive) and hasMore (its only boolean primitive).
            val (payload, images) = findImages(root)
                ?: throw Exception("Reader response format changed. Open the chapter in WebView.")

            images.forEach { image ->
                image.imageUrlOrNull()?.let { pages.add(Page(pages.size, imageUrl = it)) }
            }

            cursor = payload.values.filterIsInstance<JsonPrimitive>()
                .firstOrNull { it.isString }?.content ?: ""
            run = payload.values.filterIsInstance<JsonPrimitive>()
                .firstOrNull { it.booleanOrNull != null }?.boolean ?: false
        }

        return pages
    }

    // The real image url = the http string whose path is an actual image. Each image object also
    // carries a decoy http string (the admin-ajax endpoint), so a plain "starts with http" is not enough.
    private fun JsonElement.imageUrlOrNull(): String? = (this as? JsonObject)?.values
        ?.filterIsInstance<JsonPrimitive>()
        ?.map { it.content }
        ?.firstOrNull { it.startsWith("http") && imageExtRegex.containsMatchIn(it) }

    private fun JsonArray.isImageArray(): Boolean = firstOrNull()?.imageUrlOrNull() != null

    // Find the array of image objects anywhere in the tree and return it with its parent object.
    private fun findImages(element: JsonElement): Pair<JsonObject, JsonArray>? {
        when (element) {
            is JsonObject -> {
                element.values.forEach { if (it is JsonArray && it.isImageArray()) return element to it }
                element.values.forEach { child -> findImages(child)?.let { return it } }
            }
            is JsonArray -> element.forEach { child -> findImages(child)?.let { return it } }
            else -> {}
        }
        return null
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
