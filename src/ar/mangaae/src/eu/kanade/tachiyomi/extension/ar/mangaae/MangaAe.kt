package eu.kanade.tachiyomi.extension.ar.mangaae

import android.app.Application
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.BuildConfig
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaAe : ParsedHttpSource(), ConfigurableSource {

    override val name = "مانجا العرب"

    override val baseUrl by lazy { getPrefBaseUrl() }

    private val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val lang = "ar"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Origin", baseUrl)

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga/page:$page", headers)

    override fun popularMangaSelector() = "div.mangacontainer"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        thumbnail_url = element.selectFirst("img")?.run {
            attr("data-pagespeed-lazy-src").ifEmpty { attr("src") }
        }
        element.selectFirst("div.mangacontainer a.manga")!!.run {
            title = text()
            setUrlWithoutDomain(absUrl("href"))
        }
    }

    override fun popularMangaNextPageSelector() = "div.pagination a:last-child:not(.active)"

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int) = GET(baseUrl, headers)

    override fun latestUpdatesSelector() = "div.popular-manga-container"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        thumbnail_url = element.selectFirst("img")?.run {
            attr("data-pagespeed-lazy-src").ifEmpty { attr("src") }
        }
        setUrlWithoutDomain(element.selectFirst("a:has(img)")!!.attr("href"))
        title = element.selectFirst("a:last-child")!!.text()
    }

    override fun latestUpdatesNextPageSelector() = null

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = buildString {
            append("$baseUrl/manga/search:$query|page:$page")
            filters.firstOrNull { it is OrderByFilter }
                ?.takeUnless { it.state == 0 }
                ?.also {
                    val filter = it as OrderByFilter
                    append("|order:${filter.toUriPart()}")
                }
            append("|arrange:minus")
        }
        return GET(url.toHttpUrl(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val infoElement = document.selectFirst("div.indexcontainer")!!
        // Essential info, a NPE may be understandable
        with(infoElement) {
            title = selectFirst("h1.EnglishName")!!.text().removeSurrounding("(", ")")
            author = selectFirst("div.manga-details-author h4")?.text()
            artist = author
            thumbnail_url = selectFirst("img.manga-cover")?.attr("src")
        }

        // Additional info
        infoElement.selectFirst("div.manga-details-extended")?.run {
            status = parseStatus(selectFirst("td h4")?.text().orEmpty())
            genre = select("a[href*=tag]").eachText().joinToString()
            description = selectFirst("h4[style*=overflow-y]")?.text()
        }
    }

    private fun parseStatus(status: String) = when {
        status.contains("مستمرة") -> SManga.ONGOING
        status.contains("مكتملة") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================
    override fun chapterListSelector() = "ul.new-manga-chapters > li a"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href").removeSuffix("/1/") + "/0/allpages")
        name = "\u061C" + element.text() // Add unicode ARABIC LETTER MARK to ensure all titles are right to left
    }

    // =============================== Pages ================================
    override fun pageListParse(document: Document): List<Page> {
        return document.select("div#showchaptercontainer img").mapIndexed { index, item ->
            Page(index, "", item.attr("src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw Exception("Not used")

    // ============================== Filters ===============================
    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private class OrderByFilter : UriPartFilter(
        "الترتيب حسب",
        arrayOf(
            Pair("اختيار", ""),
            Pair("اسم المانجا", "english_name"),
            Pair("تاريخ النشر", "release_date"),
            Pair("عدد الفصول", "chapter_count"),
            Pair("الحالة", "status"),
        ),
    )

    override fun getFilterList() = FilterList(OrderByFilter())

    // ============================== Settings ==============================
    companion object {
        private const val RESTART_TACHIYOMI = ".لتطبيق الإعدادات الجديدة Tachiyomi أعد تشغيل"
        private const val BASE_URL_PREF_TITLE = "تعديل الرابط"
        private const val BASE_URL_PREF_DEFAULT = "https://manga.ae"
        private const val BASE_URL_PREF = "overrideBaseUrl_v${BuildConfig.VERSION_CODE}"
        private const val BASE_URL_PREF_SUMMARY = ".للاستخدام المؤقت. تحديث التطبيق سيؤدي الى حذف الإعدادات"
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = BASE_URL_PREF_TITLE
            summary = BASE_URL_PREF_SUMMARY
            setDefaultValue(BASE_URL_PREF_DEFAULT)
            dialogTitle = BASE_URL_PREF_TITLE

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, RESTART_TACHIYOMI, Toast.LENGTH_LONG).show()
                true
            }
        }.also(screen::addPreference)
    }

    private fun getPrefBaseUrl(): String = preferences.getString(BASE_URL_PREF, BASE_URL_PREF_DEFAULT)!!
}
