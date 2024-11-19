package eu.kanade.tachiyomi.extension.zh.happymh

import android.app.Application
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.zh.happymh.dto.ChapterListDto
import eu.kanade.tachiyomi.extension.zh.happymh.dto.PageListResponseDto
import eu.kanade.tachiyomi.extension.zh.happymh.dto.PopularResponseDto
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

const val PREF_KEY_CUSTOM_UA = "pref_key_custom_ua_"

class Happymh : HttpSource(), ConfigurableSource {
    override val name: String = "嗨皮漫画"
    override val lang: String = "zh"
    override val supportsLatest: Boolean = true
    override val baseUrl: String = "https://m.happymh.com"
    private val json: Json by injectLazy()

    private val preferences = Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    init {
        val oldUa = preferences.getString("userAgent", null)
        if (oldUa != null) {
            val editor = preferences.edit().remove("userAgent")
            if (oldUa.isNotBlank()) editor.putString(PREF_KEY_CUSTOM_UA, oldUa)
            editor.apply()
        }
    }

    private val rewriteOctetStream: Interceptor = Interceptor { chain ->
        val originalResponse: Response = chain.proceed(chain.request())
        if (originalResponse.headers("Content-Type").contains("application/octet-stream") && originalResponse.request.url.toString().contains(".jpg")) {
            val orgBody = originalResponse.body.source()
            val newBody = orgBody.asResponseBody("image/jpeg".toMediaType())
            originalResponse.newBuilder()
                .body(newBody)
                .build()
        } else {
            originalResponse
        }
    }

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(rewriteOctetStream)
        .build()

    override fun headersBuilder(): Headers.Builder {
        val builder = super.headersBuilder()
        val userAgent = preferences.getString(PREF_KEY_CUSTOM_UA, "")!!
        return if (userAgent.isNotBlank()) {
            builder.set("User-Agent", userAgent)
        } else {
            builder
        }
    }

    // Popular

    // Requires login, otherwise result is the same as latest updates
    override fun popularMangaRequest(page: Int): Request {
        val header = headersBuilder().add("referer", "$baseUrl/latest").build()
        return GET("$baseUrl/apis/c/index?pn=$page&series_status=-1&order=views", header)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<PopularResponseDto>().data

        val items = data.items.map {
            SManga.create().apply {
                title = it.name
                url = it.url
                thumbnail_url = it.cover
            }
        }
        val hasNextPage = data.isEnd.not()

        return MangasPage(items, hasNextPage)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        val header = headersBuilder().add("referer", "$baseUrl/latest").build()
        return GET("$baseUrl/apis/c/index?pn=$page&series_status=-1&order=last_date", header)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val body = FormBody.Builder()
            .addEncoded("searchkey", query)
            .add("v", "v2.13")
            .build()

        val header = headersBuilder()
            .add("referer", "$baseUrl/sssearch")
            .build()

        return POST("$baseUrl/v2.0/apis/manga/ssearch", header, body)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        return MangasPage(popularMangaParse(response).mangas, false)
    }

    // Details

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        title = document.selectFirst("div.mg-property > h2.mg-title")!!.text()
        thumbnail_url = document.selectFirst("div.mg-cover > mip-img")!!.attr("abs:src")
        author = document.selectFirst("div.mg-property > p.mg-sub-title:nth-of-type(2)")!!.text()
        artist = author
        genre = document.select("div.mg-property > p.mg-cate > a").eachText().joinToString(", ")
        description = document.selectFirst("div.manga-introduction > mip-showmore#showmore")!!.text()
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val comicId = response.request.url.pathSegments.last()
        val document = response.asJsoup()
        val script = document.selectFirst("mip-data > script:containsData(chapterList)")!!.data()
        return json.decodeFromString<ChapterListDto>(script).chapterList.map {
            SChapter.create().apply {
                val chapterId = it.id
                url = "/reads/$comicId/$chapterId"
                name = it.chapterName
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return baseUrl + chapter.url
    }

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.startsWith("/v2.0/apis/manga/read")) {
            // Old format is detected
            throw Exception("请刷新章节列表")
        }
        val comicId = chapter.url.substringAfter("/reads/").substringBefore("/")
        val chapterId = chapter.url.substringAfterLast("/")
        val url = "$baseUrl/v2.0/apis/manga/read?code=$comicId&cid=$chapterId&v=v3.1302723"
        // Some chapters return 403 without this header
        val header = headersBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .set("Referer", baseUrl + chapter.url)
            .build()
        return GET(url, header)
    }

    override fun pageListParse(response: Response): List<Page> {
        return response.parseAs<PageListResponseDto>().data.scans
            // If n == 1, the image is from next chapter
            .filter { it.n == 0 }
            .mapIndexed { index, it ->
                Page(index, "", it.url)
            }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val header = headersBuilder()
            .set("Referer", "$baseUrl/")
            .build()
        return GET(page.imageUrl!!, header)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val context = screen.context

        EditTextPreference(context).apply {
            key = PREF_KEY_CUSTOM_UA
            title = "User Agent"
            summary = "留空则使用应用设置中的默认 User Agent，重启生效"

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    Headers.headersOf("User-Agent", newValue as String)
                    true
                } catch (e: Throwable) {
                    Toast.makeText(context, "User Agent 无效：${e.message}", Toast.LENGTH_LONG).show()
                    false
                }
            }
        }.let(screen::addPreference)
    }

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromStream(it.body.byteStream())
    }
}
