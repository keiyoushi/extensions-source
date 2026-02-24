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
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.util.Calendar
import java.util.Locale
import kotlin.collections.mapIndexed
import kotlin.getValue

class RaijinScans :
    HttpSource(),
    ConfigurableSource {

    override val name = "Raijin Scans"
    override val baseUrl = "https://raijin-scans.fr"
    override val lang = "fr"
    override val supportsLatest = true

    override val client = network.cloudflareClient

    private var nonce: String? = null

    private val preferences: SharedPreferences by getPreferencesLazy()
    private val nonceRegex = """"nonce"\s*:\s*"([^"]+)"""".toRegex()
    private val numberRegex = """(\d+)""".toRegex()
    private val descriptionScriptRegex = """content\.innerHTML = `([\s\S]+?)`;""".toRegex()
    private val rmdRegex = """window\._rmd\s*=\s*["']([^"']+)["']""".toRegex()
    private val rmkRegex = """window\._rmk\s*=\s*["']([^"']+)["']""".toRegex()

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

    private fun parseStatus(status: String?): Int = when {
        status == null -> SManga.UNKNOWN
        status.contains("En cours", ignoreCase = true) -> SManga.ONGOING
        status.contains("Terminé", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ========================= Chapter List ==========================
    override fun chapterListParse(response: Response): List<SChapter> {
        val showPremium = preferences.getBoolean(
            SHOW_PREMIUM_KEY,
            SHOW_PREMIUM_DEFAULT,
        )
        val elements = response
            .asJsoup()
            .select("ul.scroll-sm li.item")
            .filterNot { !showPremium && it.selectFirst("a.cairo-premium") != null }
        val chapters = elements.map { e ->
            chapterFromElement(e, response.request.url.toString())
        }
        return chapters
    }
    private fun chapterFromElement(element: Element, mangaUrl: String): SChapter = SChapter.create().apply {
        val premium = element.selectFirst("a.cairo-premium") != null
        val link = element.selectFirst("a")!!
        val title = link.attr("title")
        var endUrl: String
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

        val result = document.select("div.protected-image-data")

        return result.mapIndexed { index, element ->
            val encodedUrl = element.attr("data-src")
            val imageUrl = String(Base64.decode(encodedUrl, Base64.DEFAULT))
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used.")

    // ========================== Preference =============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        CheckBoxPreference(screen.context).apply {
            key = SHOW_PREMIUM_KEY
            title = "Show premium chapters"
            summary = "Show paid chapters (identified by 🔒) in the list."
            setDefaultValue(SHOW_PREMIUM_DEFAULT)
        }.also(screen::addPreference)
    }

    companion object {
        private const val SHOW_PREMIUM_KEY = "show_premium_chapters"
        private const val SHOW_PREMIUM_DEFAULT = false
    }
}
