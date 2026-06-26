package eu.kanade.tachiyomi.extension.id.cosmicscansid

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.randomua.addRandomUAPreference
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferences
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class CosmicScansID :
    MangaThemesia(
        "CosmicScans.id",
        "https://lc2.cosmicscans.to",
        "id",
        dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("id")),
    ),
    ConfigurableSource {

    private val defaultBaseUrl: String = super.baseUrl

    private val preferences = getPreferences {
        getString(DEFAULT_BASE_URL_PREF, defaultBaseUrl).let { domain ->
            if (domain != defaultBaseUrl) {
                edit()
                    .putString(BASE_URL_PREF, defaultBaseUrl)
                    .putString(DEFAULT_BASE_URL_PREF, defaultBaseUrl)
                    .apply()
            }
        }
    }

    override fun headersBuilder() = super.headersBuilder()
        .setRandomUserAgent()

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    private val isCi = System.getenv("CI") == "true"

    override val baseUrl: String get() = when {
        isCi -> defaultBaseUrl
        else -> preferences.prefBaseUrl
    }

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(20, 4.seconds)
        .build()

    override val hasProjectPage = true

    override val projectPageString = "/projects"

    private var cachedBaseUrl: String? = null
    private var SharedPreferences.prefBaseUrl: String
        get() {
            if (cachedBaseUrl == null) {
                cachedBaseUrl = getString(BASE_URL_PREF, defaultBaseUrl)!!
            }
            return cachedBaseUrl!!
        }
        set(value) {
            cachedBaseUrl = value
            edit().putString(BASE_URL_PREF, value).apply()
        }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isBlank()) {
            return super.searchMangaRequest(page, query, filters)
        }

        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("page/$page/")
            .addQueryParameter("s", query)

        return GET(url.build(), headers)
    }

    override val seriesThumbnailSelector = "${super.seriesThumbnailSelector}, .bigcover img, .poster img, .manga-thumb img"

    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        if (!thumbnail_url.isValidThumbnail()) {
            thumbnail_url = document.findCover()
        }
    }

    override fun chapterListSelector() = listOf(
        "div.bxcl li:has(a[href])",
        "div.cl li:has(a[href])",
        "#chapterlist li:has(a[href])",
        "div.eplister li:has(a[href])",
        "ul li:has(div.chbox):has(div.eph-num):has(a[href])",
    ).joinToString()

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = client.newCall(chapterListRequest(manga))
        .asObservableSuccess()
        .flatMap { response ->
            val document = response.asJsoup()
            countViews(document)

            val normalChapters = parseNormalChapters(document)

            document.takeIf { it.extractMangaId() != null }
                ?.let { fetchAjaxChapters(it, normalChapters) }
                ?: fetchAjaxChaptersFromLatestChapter(normalChapters)
        }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        countViews(document)

        return mergeChapters(
            normalChapters = parseNormalChapters(document),
            ajaxChapters = emptyList(),
        )
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val urlElement = element.selectFirst("a[href]")!!
        val chapterUrl = urlElement.absUrl("href").ifBlank { urlElement.attr("href") }
        val chapterText = element.text()

        setUrlWithoutDomain(chapterUrl.normalizeChapterUrl())
        chapter_number = chapterNumberFrom(chapterUrl, chapterText)
        name = chapterNameFrom(chapterUrl, chapterText)
        date_upload = element.selectFirst(".chapterdate, .epl-date, time")
            ?.let { dateElement ->
                dateElement.attr("datetime").ifBlank { dateElement.text() }.parseCosmicChapterDate()
            }
            .takeIf { it != 0L }
            ?: chapterText.parseCosmicChapterDate()
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val chapterUrl = document.location()

        val scriptPages = parseReaderScriptImages(document, chapterUrl)
        if (scriptPages.isNotEmpty()) {
            return scriptPages
        }

        return document.select("div#readerarea img, .reading-content img, img.ts-main-image")
            .mapNotNull { img ->
                img.attr("abs:data-src")
                    .ifBlank { img.attr("abs:data-lazy-src") }
                    .ifBlank { img.attr("abs:data-cfsrc") }
                    .ifBlank { img.attr("abs:src") }
                    .takeIf { it.isValidPageImage() }
            }
            .distinct()
            .mapIndexed { index, imageUrl ->
                Page(index, chapterUrl, imageUrl)
            }
    }

    private fun parseNormalChapters(document: Document): List<SChapter> = document.select(chapterListSelector()).map(::chapterFromElement)

    private fun fetchAjaxChaptersFromLatestChapter(normalChapters: List<SChapter>): Observable<List<SChapter>> {
        val latestChapter = normalChapters.firstOrNull()
            ?: return Observable.just(mergeChapters(normalChapters, emptyList()))

        return client.newCall(GET("$baseUrl${latestChapter.url}", headers))
            .asObservableSuccess()
            .flatMap { response ->
                val document = response.asJsoup()
                if (document.extractMangaId() == null) {
                    Observable.just(mergeChapters(normalChapters, emptyList()))
                } else {
                    fetchAjaxChapters(document, normalChapters)
                }
            }
            .onErrorReturn {
                mergeChapters(normalChapters, emptyList())
            }
    }

    private fun fetchAjaxChapters(document: Document, normalChapters: List<SChapter>): Observable<List<SChapter>> {
        val request = ajaxChaptersRequest(document)
            ?: return Observable.just(mergeChapters(normalChapters, emptyList()))

        return client.newCall(request)
            .asObservableSuccess()
            .map { response ->
                mergeChapters(
                    normalChapters = normalChapters,
                    ajaxChapters = parseAjaxChapters(response.body.string()),
                )
            }
            .onErrorReturn {
                mergeChapters(normalChapters, emptyList())
            }
    }

    private fun ajaxChaptersRequest(document: Document): Request? {
        val mangaId = document.extractMangaId() ?: return null
        val formBody = FormBody.Builder()
            .add("action", "get_chapters")
            .add("id", mangaId)
            .build()

        val ajaxHeaders = headersBuilder()
            .set("X-Requested-With", "XMLHttpRequest")
            .set("Referer", document.location())
            .build()

        return POST(document.extractAjaxUrl(), ajaxHeaders, formBody)
    }

    private fun parseAjaxChapters(response: String): List<SChapter> {
        val document = Jsoup.parseBodyFragment(response, baseUrl)

        return document.select("option[value]:not([value=''])").map { option ->
            SChapter.create().apply {
                val chapterUrl = option.absUrl("value").ifBlank { option.attr("value") }
                val chapterText = option.text()

                setUrlWithoutDomain(chapterUrl.normalizeChapterUrl())
                chapter_number = chapterNumberFrom(chapterUrl, chapterText)
                name = chapterNameFrom(chapterUrl, chapterText)
            }
        }
    }

    private fun Document.extractAjaxUrl(): String = AJAX_URL_REGEX.find(toString())
        ?.groupValues
        ?.get(1)
        ?.replace("\\/", "/")
        ?: "$baseUrl/wp-admin/admin-ajax.php"

    private fun Document.extractMangaId(): String? {
        val html = toString()

        MANGA_ID_REGEXES.forEach { regex ->
            regex.find(html)?.groupValues?.get(1)?.let { return it }
        }

        return null
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addRandomUAPreference()

        EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = "Edit source URL"
            summary = "For temporary use, if the extension is updated the change will be lost."
            dialogTitle = title
            dialogMessage = "Default URL:\n$defaultBaseUrl"
            setDefaultValue(defaultBaseUrl)
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Restart the application to apply the changes", Toast.LENGTH_LONG).show()
                true
            }
        }.also { screen.addPreference(it) }
    }

    companion object {
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
    }
}
