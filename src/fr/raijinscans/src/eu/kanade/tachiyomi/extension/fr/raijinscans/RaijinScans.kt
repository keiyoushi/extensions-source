package eu.kanade.tachiyomi.extension.fr.raijinscans

import android.content.SharedPreferences
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
import keiyoushi.annotation.Source
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.net.URI
import java.util.Calendar
import java.util.Locale
import kotlin.getValue

@Source
abstract class RaijinScans :
    HttpSource(),
    ConfigurableSource {

    override val supportsLatest = true

    private val hasPremiumChapters = true

    private var nonce: String? = null

    private val preferences: SharedPreferences by getPreferencesLazy()
    private val nonceRegex = """"nonce"\s*:\s*"([^"]+)"""".toRegex()
    private val numberRegex = """(\d+)""".toRegex()

    // Capture only within a single template literal (no backtick inside). The page also has a
    // "view all" toggle whose `content.innerHTML = ``` assignments are empty; a [\s\S]+? capture
    // would span across them and yield garbage, so a description-less page must fail to match here
    // and fall back to div.description-content.
    private val descriptionScriptRegex = """content\.innerHTML = `([^`]+)`;""".toRegex()

    private val scriptManager by lazy { ReaderScriptManager(client, preferences) }
    private val pageListInterpreter by lazy { PageListInterpreter(client) }

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

    // Descrambler logic lives in an external JS bundle ([ReaderScriptManager]) run in a sandboxed
    // WebView ([PageListInterpreter]), so it can be updated server-side without a new APK.
    // Network runs here, off the main thread, not in pageListParse (parse methods must not block).
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.fromCallable {
        client.newCall(pageListRequest(chapter)).execute().use(::pageList)
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException("Handled in fetchPageList.")

    private fun pageList(response: Response): List<Page> {
        val html = response.body.string()
        val document = Jsoup.parse(html, baseUrl)

        // /connexion isn't in the chapter list, so we route to /{manga}/{chapter} which lands on the
        // premium page (or a 404). Treat either signal as premium.
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

        return runBlocking {
            val attempt: suspend (String) -> List<Page> = { script ->
                pageListInterpreter.getPages(
                    script = script,
                    baseUrl = baseUrl,
                    chapterUrl = chapterUrl,
                    html = html,
                    ajaxHeaders = ajaxHeaders,
                    maxPageRequests = MAX_PAGE_REQUESTS,
                )
            }

            val script = scriptManager.getScript()
            try {
                attempt(script)
            } catch (e: Exception) {
                // Cached script may be stale while a fixed one is published. Refetch and retry once;
                // if identical, retrying is pointless, so surface the original error.
                val fresh = scriptManager.refreshScript()
                if (fresh == script) throw e
                attempt(fresh)
            }
        }
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
