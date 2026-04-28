package eu.kanade.tachiyomi.extension.ru.nudemoon

import android.content.SharedPreferences
import android.webkit.CookieManager
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferences
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.random.Random

class Nudemoon :
    HttpSource(),
    ConfigurableSource {
    override val name = "Nude-Moon"
    override val lang = "ru"
    override val supportsLatest = true
    private val preferences: SharedPreferences = getPreferences()
    override val baseUrl by lazy { getPrefBaseUrl() }

    private val dateParseRu = SimpleDateFormat("d MMMM yyyy", Locale("ru"))
    private val cookieManager by lazy { CookieManager.getInstance() }
    private val userAgentRandomizer = "${Random.nextInt().absoluteValue}"

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 10; SM-G980F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.$userAgentRandomizer Mobile Safari/537.36")
        .add("Referer", baseUrl)

    init {
        cookieManager.setCookie(baseUrl, "nm_mobile=1; Domain=" + baseUrl.split("//")[1])
    }

    private val cookiesHeader by lazy {
        mapOf("NMfYa" to "1", "nm_mobile" to "1").entries.joinToString(separator = "; ", postfix = ";") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
    }

    override val client = network.cloudflareClient.newBuilder()
        .addNetworkInterceptor { chain ->
            val newReq = chain.request().newBuilder()
                .addHeader("Cookie", cookiesHeader)
                .addHeader("Referer", baseUrl)
                .build()
            chain.proceed(newReq)
        }.build()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/all_manga?views&rowstart=${30 * (page - 1)}", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/all_manga?date&rowstart=${30 * (page - 1)}", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotEmpty()) {
            "$baseUrl/search?stext=${URLEncoder.encode(query, "CP1251")}&rowstart=${30 * (page - 1)}"
        } else {
            val currentFilters = if (filters.isEmpty()) getFilterList() else filters
            val genreList = currentFilters.firstInstanceOrNull<GenreList>()
            val orderFilter = currentFilters.firstInstanceOrNull<OrderBy>()

            val genres = buildString {
                genreList?.state?.forEach { f ->
                    if (f.state) append(f.id).append('+')
                }
            }

            val orderIndex = orderFilter?.state?.index ?: 1
            val order = if (genres.isNotEmpty()) {
                arrayOf("&date", "&views", "&like")[orderIndex]
            } else {
                arrayOf("all_manga?date", "all_manga?views", "all_manga?like")[orderIndex]
            }

            val path = if (genres.isNotEmpty()) "tags/${genres.dropLast(1)}$order" else order
            "$baseUrl/$path&rowstart=${30 * (page - 1)}"
        }
        return GET(url, headers)
    }

    private val mangaSelector = "table.news_pic2"
    private val nextPageSelector = "a.small:contains(>)"

    private fun parseMangaElement(element: Element): SManga? {
        val manga = SManga.create()
        manga.thumbnail_url = element.selectFirst("a img")?.attr("abs:src")
        element.selectFirst("a:has(h2)")?.let {
            manga.title = it.text().substringBefore(" / ").substringBefore(" №")
            manga.setUrlWithoutDomain(it.attr("href"))
            return manga
        }
        return null
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(mangaSelector).mapNotNull(::parseMangaElement)
        val hasNextPage = document.selectFirst(nextPageSelector) != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)
    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val manga = SManga.create()
        val infoElement = document.selectFirst(mangaSelector)
        manga.title = document.selectFirst("h1")?.text()?.substringBefore(" / ")?.substringBefore(" №") ?: ""
        manga.author = infoElement?.selectFirst("a[href*=mangaka]")?.text()
        manga.genre = infoElement?.select("div.tag-links a")?.joinToString { it.text() }
        manga.description = document.selectFirst(".description")?.text()
        manga.thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("abs:content")
        return manga
    }

    override fun chapterListParse(response: Response): List<SChapter> = mutableListOf<SChapter>().apply {
        val document = response.asJsoup()
        document.selectFirst("td.button a:contains(Все главы)")?.let { allPageElement ->
            var pageListLink = allPageElement.absUrl("href")
            var hasNextPage = true
            while (hasNextPage) {
                client.newCall(GET(pageListLink, headers)).execute().use { res ->
                    if (!res.isSuccessful) throw Exception("HTTP error ${res.code}")
                    val pageDoc = res.asJsoup()
                    val chapters = pageDoc.select(mangaSelector).mapNotNull { element ->
                        SChapter.create().apply {
                            val nameAndUrl = element.selectFirst("tr[valign=top] a:has(h2)")
                            name = nameAndUrl?.selectFirst("h2")?.text() ?: return@mapNotNull null
                            setUrlWithoutDomain(nameAndUrl.attr("abs:href"))
                            val informBlock = element.selectFirst("tr[valign=top] td[align=left]")
                            scanlator = informBlock?.selectFirst("a[href*=perevod]")?.text()
                            date_upload = informBlock?.selectFirst("span.small2")?.text()?.let { text ->
                                dateParseRu.tryParse(text.replace("Май", "Мая"))
                            } ?: 0L
                            chapter_number = name.substringAfter("№").substringBefore(" ").toFloatOrNull() ?: -1f
                        }
                    }
                    if (chapters.isEmpty()) {
                        add(chapterFromSinglePage(document, response.request.url.toString()))
                        break
                    }
                    addAll(chapters)
                    val nextPageElement = pageDoc.selectFirst(nextPageSelector)
                    if (nextPageElement != null) {
                        pageListLink = nextPageElement.absUrl("href")
                    } else {
                        hasNextPage = false
                    }
                }
            }
        } ?: run {
            add(chapterFromSinglePage(document, response.request.url.toString()))
        }
    }

    private fun chapterFromSinglePage(document: Document, responseUrl: String): SChapter = SChapter.create().apply {
        val chapterName = document.selectFirst("table td.bg_style1 h1")?.text()
        name = "$chapterName Сингл"
        setUrlWithoutDomain(responseUrl)
        if (url.contains(baseUrl)) {
            url = url.replace(baseUrl, "")
        }
        scanlator = document.selectFirst("table.news_pic2 a[href*=perevod]")?.text()
        date_upload = document.selectFirst("table.news_pic2 span.small2")?.text()?.let { text ->
            dateParseRu.tryParse(text.replace("Май", "Мая"))
        } ?: 0L
        chapter_number = 0F
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pages = document.select("""img[title~=.+][loading="lazy"]""").mapIndexed { index, img ->
            Page(index, imageUrl = img.attr("abs:data-src"))
        }
        if (pages.isEmpty() && !cookieManager.getCookie(baseUrl).contains("fusion_user")) {
            throw Exception("Страницы не найдены. Возможно необходима авторизация в WebView")
        }
        return pages
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = DOMAIN_PREF
            title = DOMAIN_TITLE
            setDefaultValue(DOMAIN_DEFAULT)
            dialogTitle = DOMAIN_TITLE
            dialogMessage = "Default URL:\n\t$DOMAIN_DEFAULT"
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Для смены домена необходимо перезапустить приложение с полной остановкой.", Toast.LENGTH_LONG).show()
                true
            }
        }.let(screen::addPreference)
    }

    private fun getPrefBaseUrl(): String = preferences.getString(DOMAIN_PREF, DOMAIN_DEFAULT)!!

    init {
        preferences.getString(DEFAULT_DOMAIN_PREF, null).let { defaultBaseUrl ->
            if (defaultBaseUrl != DOMAIN_DEFAULT) {
                preferences.edit()
                    .putString(DOMAIN_PREF, DOMAIN_DEFAULT)
                    .putString(DEFAULT_DOMAIN_PREF, DOMAIN_DEFAULT)
                    .apply()
            }
        }
    }

    companion object {
        private const val DOMAIN_PREF = "Домен"
        private const val DEFAULT_DOMAIN_PREF = "pref_default_domain"
        private const val DOMAIN_TITLE = "Домен"
        private const val DOMAIN_DEFAULT = "https://nude-moon.org"
    }
}
