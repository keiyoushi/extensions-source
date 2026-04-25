package eu.kanade.tachiyomi.extension.es.insanosscan

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
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class InsanosScan :
    HttpSource(),
    ConfigurableSource {

    override val name = "InsanosScan"
    override val baseUrl = "https://insanoslibrary.com"
    override val lang = "es"
    override val supportsLatest = true
    override val versionId = 2

    override val client = network.cloudflareClient

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale("es"))

    // ========================= Preferences =========================

    private val preferences = getPreferences()

    private val showPaidChapters: Boolean
        get() = preferences.getBoolean(PREF_SHOW_PAID, false)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        CheckBoxPreference(screen.context).apply {
            key = PREF_SHOW_PAID
            title = "Mostrar capítulos de pago"
            summary = "Incluye capítulos que requieren monedas para leer"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    // ========================= Nonce =========================

    private val nonce: String by lazy {
        val doc = client.newCall(GET(baseUrl, headers)).execute().use { it.asJsoup() }
        val b64 = doc.selectFirst("script#adar-main-js-extra")
            ?.attr("src")
            ?.removePrefix("data:text/javascript;base64,")
            ?: return@lazy ""
        val js = String(Base64.decode(b64, Base64.DEFAULT))
        NONCE_REGEX.find(js)?.groupValues?.get(1) ?: ""
    }

    // ========================= Popular =========================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga/?orderby=views&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("article.catalog-card").map { element ->
            SManga.create().apply {
                val link = element.selectFirst("a.catalog-card__link")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = element.selectFirst("h2.catalog-card__title")!!.text()
                thumbnail_url = element.selectFirst("img.catalog-card__cover")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("div.catalog-pagination a.page-numbers.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ========================= Latest =========================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga/?orderby=date&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ========================= Search =========================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val body = FormBody.Builder()
            .add("action", "adar_search")
            .add("nonce", nonce)
            .add("query", query)
            .build()
        return POST("$baseUrl/wp-admin/admin-ajax.php", headers, body)
    }

    @Serializable
    private data class SearchResponse(
        val data: List<SearchItem>? = null,
    )

    @Serializable
    private data class SearchItem(
        val url: String,
        val title: String,
        val cover: String? = null,
    )

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SearchResponse>()
        val mangas = (result.data ?: return MangasPage(emptyList(), false)).map { item ->
            SManga.create().apply {
                setUrlWithoutDomain(item.url)
                title = item.title
                thumbnail_url = item.cover?.ifEmpty { null }
            }
        }
        return MangasPage(mangas, false)
    }

    // ========================= Details =========================

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.series-main-title")!!.text()
            thumbnail_url = document.selectFirst("img.series-cover-img")?.absUrl("src")
            description = document.selectFirst("div.synopsis-content")?.text()
            status = when (
                document.selectFirst("span.data-badge--status")?.text()?.lowercase()
            ) {
                "en emisión" -> SManga.ONGOING
                "finalizado" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            genre = document.select("td.genres-cell a.genre-pill")
                .joinToString { it.text() }
        }
    }

    // ========================= Chapters =========================

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl${manga.url}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val lockedPaths = parsedLockedPaths(document)

        return document.select("div.chapters-list a.chapter-row").mapNotNull { element ->
            val href = element.attr("href")
                .removePrefix(baseUrl)
                .trimEnd('/')
                .plus("/")

            if (!showPaidChapters && href in lockedPaths) return@mapNotNull null

            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = buildString {
                    append(
                        element.selectFirst("span.chapter-row__num")?.text()
                            ?: element.selectFirst("span.chapter-row__title")?.text()
                            ?: "Capítulo",
                    )
                    // Append a coin indicator so users know it's paid
                    if (href in lockedPaths) append(" 🔒")
                }
                date_upload = dateFormat.tryParse(
                    element.selectFirst("span.chapter-row__date")?.text(),
                )
            }
        }
    }

    private fun parsedLockedPaths(document: Document): Set<String> {
        val scriptBody = document.select("script:not([src])").firstOrNull { script ->
            script.data().contains("var locked")
        }?.data() ?: return emptySet()

        val raw = LOCKED_REGEX.find(scriptBody)?.groupValues?.get(1) ?: return emptySet()

        return runCatching {
            Json.parseToJsonElement(raw).jsonObject
                .entries
                .filter { (_, v) -> v.jsonPrimitive.int > 0 }
                .map { (k, _) -> k }
                .toSet()
        }.getOrDefault(emptySet())
    }

    // ========================= Pages =========================

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.reader-pages ~ div img, div.reader-pages + div img")
            .mapIndexed { index, img ->
                Page(index, "", img.absUrl("src").ifEmpty { img.absUrl("data-src") })
            }
            .ifEmpty {
                document.select("body.reader-body img[src*='adar_manga']")
                    .mapIndexed { index, img -> Page(index, "", img.absUrl("src")) }
            }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    companion object {
        private const val PREF_SHOW_PAID = "show_paid_chapters"

        private val NONCE_REGEX = Regex(""""nonce"\s*:\s*"([^"]+)"""")
        private val LOCKED_REGEX = Regex("""var locked\s*=\s*(\{[^;]+\});""")
    }
}
