package eu.kanade.tachiyomi.extension.zh.manwa

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Manwa : ParsedHttpSource(), ConfigurableSource {
    override val name: String = "漫蛙"
    override val lang: String = "zh"
    override val supportsLatest: Boolean = true
    private val json: Json by injectLazy()
    private val preferences: SharedPreferences =
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    override val baseUrl = "https://" + MIRROR_ENTRIES.run { this[preferences.getString(MIRROR_KEY, "0")!!.toInt().coerceAtMost(size)] }

    private val rewriteOctetStream: Interceptor = Interceptor { chain ->
        val originalResponse: Response = chain.proceed(chain.request())
        if (originalResponse.request.url.toString().endsWith("?v=20220724")) {
            // Decrypt images in mangas
            val orgBody = originalResponse.body.bytes()
            val key = "my2ecret782ecret".toByteArray()
            val aesKey = SecretKeySpec(key, "AES")
            val cipher = Cipher.getInstance("AES/CBC/NOPADDING")
            cipher.init(Cipher.DECRYPT_MODE, aesKey, IvParameterSpec(key))
            val result = cipher.doFinal(orgBody)
            val newBody = result.toResponseBody("image/webp".toMediaTypeOrNull())
            originalResponse.newBuilder()
                .body(newBody)
                .build()
        } else {
            originalResponse
        }
    }

    override val client: OkHttpClient = network.client.newBuilder()
        .addNetworkInterceptor(rewriteOctetStream)
        .build()

    private val baseHttpUrl = baseUrl.toHttpUrlOrNull()

    // Popular
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/rank", headers)
    override fun popularMangaNextPageSelector(): String? = null
    override fun popularMangaSelector(): String = "#rankList_2 > a"
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.attr("title")
        url = element.attr("href")
        thumbnail_url = element.select("img").attr("data-original")
    }

    // Latest
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/getUpdate?page=${page * 15 - 15}&date=", headers)
    override fun latestUpdatesParse(response: Response): MangasPage {
        // Get image host
        val resp = client.newCall(GET("$baseUrl/update?img_host=${preferences.getString(IMAGE_HOST_KEY, IMAGE_HOST_ENTRY_VALUES[0])}")).execute()
        val document = resp.asJsoup()
        val imgHost = document.selectFirst(".manga-list-2-cover-img")!!.attr(":src").drop(1).substringBefore("'")

        val jsonObject = json.parseToJsonElement(response.body.string()).jsonObject
        val mangas = jsonObject["books"]!!.jsonArray.map {
            SManga.create().apply {
                val obj = it.jsonObject
                title = obj["book_name"]!!.jsonPrimitive.content
                url = "/book/${obj["id"]!!.jsonPrimitive.content}"
                thumbnail_url = imgHost + obj["cover_url"]!!.jsonPrimitive.content
            }
        }

        val currentPage = response.request.url.toString().substringAfter("page=").substringBefore("&").toInt()
        val totalPage = jsonObject["total"]!!.jsonPrimitive.int
        return MangasPage(mangas, totalPage > currentPage + 15)
    }

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()
    override fun latestUpdatesSelector() = throw UnsupportedOperationException()
    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = Uri.parse(baseUrl).buildUpon()
        uri.appendPath("search")
            .appendQueryParameter("keyword", query)
        return GET(uri.toString(), headers)
    }

    override fun searchMangaNextPageSelector(): String? = null
    override fun searchMangaSelector(): String = "ul.book-list > li"
    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("p.book-list-info-title")!!.text()
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
        thumbnail_url = element.selectFirst("img")!!.attr("data-original")
    }

    // Details

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst(".detail-main-info-title")!!.text()
        thumbnail_url = document.selectFirst("div.detail-main-cover > img")!!.attr("data-original")
        author = document.select("p.detail-main-info-author > span.detail-main-info-value > a").text()
        artist = author
        genre = document.select("div.detail-main-info-class > a.info-tag").eachText().joinToString(", ")
        description = document.selectFirst("#detail > p.detail-desc")!!.text()
    }

    // Chapters

    override fun chapterListSelector(): String = "ul#detail-list-select > li > a"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        url = element.attr("href")
        name = element.text()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        client.newCall(GET("$baseUrl/static/images/pv.gif")).execute()
        return super.fetchPageList(chapter)
    }

    // Pages
    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$baseUrl${chapter.url}?img_host=${preferences.getString(IMAGE_HOST_KEY, IMAGE_HOST_ENTRY_VALUES[0])}", headers)
    }

    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        val cssQuery = "#cp_img > div.img-content > img[data-r-src]"
        val elements = document.select(cssQuery)
        if (elements.size == 3) {
            val darkReader = document.selectFirst("#cp_img p")
            if (darkReader != null) {
                if (preferences.getBoolean(AUTO_CLEAR_COOKIE_KEY, false)) {
                    clearCookies()
                }
                throw Exception(darkReader.text())
            }
        }
        elements.forEachIndexed { index, it ->
            add(Page(index, "", it.attr("data-r-src")))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = MIRROR_KEY
            title = "使用镜像网址"
            entries = MIRROR_ENTRIES
            entryValues = Array(entries.size, Int::toString)
            setDefaultValue("0")
        }.let { screen.addPreference(it) }

        ListPreference(screen.context).apply {
            key = IMAGE_HOST_KEY
            title = "图源"
            entries = IMAGE_HOST_ENTRIES
            entryValues = IMAGE_HOST_ENTRY_VALUES
            setDefaultValue(IMAGE_HOST_ENTRY_VALUES[0])
        }.let { screen.addPreference(it) }

        CheckBoxPreference(screen.context).apply {
            key = AUTO_CLEAR_COOKIE_KEY
            title = "自动删除 Cookie"

            setDefaultValue(false)
        }.let { screen.addPreference(it) }
    }

    private fun clearCookies() {
        if (baseHttpUrl == null) {
            return
        }
        val cookies = client.cookieJar.loadForRequest(baseHttpUrl)
        val obsoletedCookies = cookies.map {
            val cookie = Cookie.parse(baseHttpUrl, "${it.name}=; Max-Age=-1")!!
            cookie
        }
        client.cookieJar.saveFromResponse(baseHttpUrl, obsoletedCookies)
    }

    companion object {
        private const val MIRROR_KEY = "MIRROR"
        private val MIRROR_ENTRIES get() = arrayOf("manwa.fun", "manwa.me", "manwav3.xyz", "manwasa.cc", "manwadf.cc")
        private const val IMAGE_HOST_KEY = "IMG_HOST"
        private val IMAGE_HOST_ENTRIES = arrayOf("图源1", "图源2", "图源3")
        private val IMAGE_HOST_ENTRY_VALUES = arrayOf("1", "2", "3")

        private const val AUTO_CLEAR_COOKIE_KEY = "CLEAR_COOKIE"
    }
}
