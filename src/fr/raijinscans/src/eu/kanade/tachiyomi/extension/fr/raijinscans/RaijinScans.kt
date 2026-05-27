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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
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
    private val manifestPushRegex =
        """window\[(?:"|')rjfr_[^"']+(?:"|')]\[window\[(?:"|')rjfr_[^"']+(?:"|')]\.length]\s*=\s*\[([^\]]+)]""".toRegex()
    private val manifestFragmentRegex = """"([0-9a-z])\.([A-Za-z0-9+/=]+)"""".toRegex()
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

        // The reader manifest is now split into ~35 base64 fragments of the form
        // "<k>.<chunk>" (k ∈ 0-9a-y), pushed onto a randomly-named window array
        // in a <script> right after the page <figure> elements. Sorting fragments
        // by their leading char and concatenating yields one base64 string that
        // decodes to [permutation, data]; logical positions in `data` are recovered
        // via permutation.indexOf(logicalIndex).
        val manifests = document.select("figure:contains(JavaScript) + script")
            .flatMap { script -> manifestPushRegex.findAll(script.data()).map { it.groupValues[1] } }
            .mapNotNull { pushBody ->
                val fragments = manifestFragmentRegex.findAll(pushBody)
                    .map { it.groupValues[1] to it.groupValues[2] }
                    .toList()
                if (fragments.isEmpty()) return@mapNotNull null
                val b64 = fragments.sortedBy { it.first }.joinToString("") { it.second }
                val outer = json.parseToJsonElement(String(Base64.decode(b64, Base64.DEFAULT))).jsonArray
                val permutation = outer[0].jsonArray.map { it.jsonPrimitive.content.toInt() }
                val data = outer[1].jsonArray
                IntArray(permutation.size) { permutation.indexOf(it) } to data
            }

        if (manifests.isEmpty()) {
            throw Exception("No reader manifest found. Open the chapter in WebView.")
        }

        val pages = mutableListOf<Page>()
        for ((logicalToPhysical, manifest) in manifests) {
            fun str(logical: Int) = manifest[logicalToPhysical[logical]].jsonPrimitive.content
            fun arr(logical: Int) = manifest[logicalToPhysical[logical]].jsonArray

            val ajaxUrl = str(M_AJAX_URL)
            val action = str(M_ACTION)
            val reqKeys = arr(M_REQ_KEYS).map { it.jsonPrimitive.content }
            val resKeys = arr(M_RES_KEYS).map { it.jsonPrimitive.content }

            var offset = str(M_OFFSET)
            var cursor = ""
            var run = true
            var guard = 0
            while (run && guard++ < MAX_PAGE_REQUESTS) {
                val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("action", action)
                    .addFormDataPart(reqKeys[0], str(M_TOKEN))
                    .addFormDataPart(reqKeys[1], str(M_NONCE))
                    .addFormDataPart(reqKeys[2], str(M_MANGA_ID))
                    .addFormDataPart(reqKeys[3], str(M_CHAPTER_ID))
                    .addFormDataPart(reqKeys[4], str(M_CHAPTER_SLUG))
                    .addFormDataPart(reqKeys[5], str(M_HOST))
                    .addFormDataPart(reqKeys[6], offset)
                    .addFormDataPart(reqKeys[7], str(M_EXTRA))
                    .addFormDataPart(reqKeys[8], str(M_INSTANCE))
                    .addFormDataPart(reqKeys[9], cursor)
                    .build()

                val root = client.newCall(POST(ajaxUrl, ajaxHeaders, body)).execute()
                    .use { it.parseAs<JsonObject>() }

                val payload = root[resKeys[1]]!!.jsonObject

                payload[resKeys[2]]!!.jsonArray.forEach { image ->
                    val url = image.jsonObject[resKeys[4]]!!.jsonPrimitive.content
                    pages.add(Page(pages.size, imageUrl = url))
                }

                offset = payload[resKeys[6]]?.jsonPrimitive?.content ?: ""
                cursor = payload[resKeys[8]]?.jsonPrimitive?.content ?: ""
                run = payload[resKeys[9]]?.jsonPrimitive?.boolean ?: false
            }
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

        // Positional indices into the decoded reader manifest array.
        private const val M_AJAX_URL = 0
        private const val M_TOKEN = 1
        private const val M_NONCE = 2
        private const val M_MANGA_ID = 3
        private const val M_CHAPTER_ID = 4
        private const val M_CHAPTER_SLUG = 5
        private const val M_HOST = 6
        private const val M_INSTANCE = 7
        private const val M_OFFSET = 8
        private const val M_EXTRA = 9
        private const val M_ACTION = 12
        private const val M_REQ_KEYS = 13
        private const val M_RES_KEYS = 14
    }
}
