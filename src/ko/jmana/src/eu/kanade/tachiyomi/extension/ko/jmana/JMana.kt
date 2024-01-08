package eu.kanade.tachiyomi.extension.ko.jmana

import android.app.Application
import android.content.SharedPreferences
import android.widget.Toast
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * JMana Source
 **/
class JMana : ConfigurableSource, ParsedHttpSource() {
    override val name = "JMana"
    override val baseUrl: String by lazy { getPrefBaseUrl()!!.removeSuffix("/") }
    override val lang: String = "ko"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", baseUrl)

    private fun String.cleanedUrl() = this.replace(" ", "%20").replace(Regex("/[0-9]+(?!.*?/)"), "")

    // Latest page has chapter number appended to the title
    private fun String.cleanedTitle() = this.removeSuffix("([0-9]+-)?[0-9]+화".toRegex().find(this)?.value ?: "").trim()

    override fun popularMangaSelector() = "div.content > div.search-result-wrap > div.img-lst-wrap > ul > li"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("div.txt-wrap > a.tit").let {
                setUrlWithoutDomain("/" + it.attr("href").cleanedUrl())
                title = it.text()
            }
            thumbnail_url = element.select("a.img-wrap > img").attr("abs:src")
        }
    }

    override fun popularMangaNextPageSelector() = throw UnsupportedOperationException("This method should not be called!")

    // Do not add page parameter if page is 1 to prevent tracking.
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/comic_list?page=${page - 1}", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }

        val hasNextPage = document.select("div.lst-btm-wrap > div.cnt-wrap > ul.pager-wrap > li.next > a").attr("href") != "#"

        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException("This method should not be called!")
    override fun searchMangaParse(response: Response) = popularMangaParse(response)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/comic_list?page=${page - 1}&keyword=$query", headers)

    override fun mangaDetailsParse(document: Document): SManga {
        val descriptionElement = document.select("div.content > div.books-list-detail > div.books-db-detail")

        val manga = SManga.create()
        descriptionElement.select("div.books-d-wrap > dl")
            .map { it.text() }
            .forEach { text ->
                when {
                    DETAIL_AUTHOR in text -> manga.author = text.substringAfter(DETAIL_AUTHOR).trim()
                    DETAIL_GENRE in text -> manga.genre = text.substringAfter(DETAIL_GENRE).trim()
                }
            }
        manga.title = descriptionElement.select("a.tit").text()
        manga.thumbnail_url = descriptionElement.select("div.books-thumnail img").attr("abs:src")
        return manga
    }

    override fun chapterListSelector() = "div.content > div.books-list-detail > div.lst-wrap > ul > li"
    private val TAG = "JMana"

    override fun chapterFromElement(element: Element): SChapter {
        val top = element.select("div.top-layout-m > div.inner > a.tit")
        val bottom = element.select("div.btm-layout-m > div.inner > p.date")
        val rawName = top.text()

        return SChapter.create().apply {
            setUrlWithoutDomain(top.attr("abs:href"))
            chapter_number = parseChapterNumber(rawName)
            name = rawName.trim()
            date_upload = parseChapterDate(bottom.text())
        }
    }

    private fun parseChapterNumber(name: String): Float {
        try {
            if (name.contains("[단편]")) return 1f
            // `특별` means `Special`, so It can be buggy. so pad `편`(Chapter) to prevent false return
            if (name.contains("번외") || name.contains("특별편")) return -2f
            val regex = Regex("([0-9]+)(?:[-.]([0-9]+))?(?:화)")
            val (ch_primal, ch_second) = regex.find(name)!!.destructured
            return (ch_primal + if (ch_second.isBlank()) "" else ".$ch_second").toFloatOrNull() ?: -1f
        } catch (e: Exception) {
            e.printStackTrace()
            return -1f
        }
    }

    private fun parseChapterDate(date: String): Long {
        return try {
            SimpleDateFormat("yy-MM-dd", Locale.getDefault()).parse(date)?.time ?: 0
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        // <img class="comicdetail" style="width:auto;min-width:auto;margin:auto;display:block"
        // data-src="https://img6.xyz.futbol/comics/jdrive01/202005/하야테처럼/하야테처럼! 1화/2d206674-93f5-4991-9420-6d63e2a00010.jpg">
        val pages = document.select("div.pdf-wrap img.comicdetail")
            .groupBy { img ->
                val imageUrl = getImageUrl(img)
                extractChapterName(imageUrl)
            }
            .maxByOrNull { it.value.size }
            ?.value
            ?.mapIndexed { i, img ->
                Page(
                    i,
                    "",
                    getImageUrl(img),
                )
            }
        return pages ?: emptyList()
    }

    /**
     * Extracts the chapter name from the image url, e.g.
     * https://img6.xyz.futbol/comics/jdrive01/202005/하야테처럼/하야테처럼! 1화/2d206674-93f5-4991-9420-6d63e2a00010.jpg
     * -> '하야테처럼! 1화'
     */
    private fun extractChapterName(imageUrl: String) =
        imageUrl.split('/').dropLast(1).lastOrNull()

    private fun getImageUrl(img: Element) =
        if (img.hasAttr("data-src")) img.attr("abs:data-src") else img.attr("abs:src")

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/comic_recent?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(latestUpdatesSelector()).map { element ->
            latestUpdatesFromElement(element)
        }

        val hasNextPage = document.select("div.lst-btm-wrap > div.cnt-wrap > ul.pager-wrap > li.next > a").attr("href") != "#"

        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesSelector() = "div.content > div.board03 > div.img-lst-wrap > ul > li"

    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("div.list-con > div.books-db").let {
                setUrlWithoutDomain(it.select("a.btn").attr("href").cleanedUrl())
                title = it.select("a.tit").text().cleanedTitle()
            }
            thumbnail_url = element.select("a.img-wrap > img").attr("abs:src")
        }
    }

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException("This method should not be called!")

    // We are able to get the image URL directly from the page list
    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("This method should not be called!")

    companion object {
        const val DETAIL_GENRE = "장르 : "
        const val DETAIL_AUTHOR = "작가 : "
        const val DETAIL_DESCRIPTION = "설명 : "
        const val DEFAULT_BASEURL = "https://jmana1.net"
        private const val BASE_URL_PREF_TITLE = "Override BaseUrl"
        private val BASE_URL_PREF = "overrideBaseUrl_v${AppInfo.getVersionName()}"
        private const val BASE_URL_PREF_SUMMARY = "For temporary uses. Update extension will erase this setting."
        private const val RESTART_TACHIYOMI = "Restart Tachiyomi to apply new setting."
    }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val baseUrlPref = androidx.preference.EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF_TITLE
            title = BASE_URL_PREF_TITLE
            summary = BASE_URL_PREF_SUMMARY
            this.setDefaultValue(DEFAULT_BASEURL)
            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "Default: $DEFAULT_BASEURL"

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(BASE_URL_PREF, newValue as String).commit()
                    Toast.makeText(screen.context, RESTART_TACHIYOMI, Toast.LENGTH_LONG).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        screen.addPreference(baseUrlPref)
    }

    private fun getPrefBaseUrl() = preferences.getString(BASE_URL_PREF, DEFAULT_BASEURL)
}
