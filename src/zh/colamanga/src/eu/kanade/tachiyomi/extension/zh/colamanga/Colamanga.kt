package eu.kanade.tachiyomi.extension.zh.colamanga

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class Colamanga : ParsedHttpSource(), ConfigurableSource {
    override val supportsLatest = true
    override val lang = "zh"
    override val name = "Cola漫画"
    override val baseUrl = "https://www.colamanga.com"

    private val preferences: SharedPreferences =
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    override val client: OkHttpClient =
        network.cloudflareClient
            .newBuilder()
            .addNetworkInterceptor(DecryptImageInterceptor)
            .connectTimeout(1, TimeUnit.MINUTES)
            .readTimeout(1, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .build()

    private val decryptor: Decryptor = Decryptor(preferences, client)

    override fun headersBuilder() =
        super.headersBuilder().add("Referer", baseUrl).add("Origin", baseUrl)

    override fun popularMangaSelector() = "li.fed-list-item"

    override fun latestUpdatesSelector() = "li.fed-list-item"

    override fun searchMangaSelector() = "div.fed-main-info dl"

    override fun chapterListSelector() = "div.all_data_list li.fed-padding"

    override fun popularMangaNextPageSelector() = "div.fed-page-info"

    override fun latestUpdatesNextPageSelector() = "div.fed-page-info"

    override fun searchMangaNextPageSelector() = "div.fed-page-info"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/show?page=$page", headers)

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/show?orderBy=update&page=$page", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // impossible to search a manga and use the filters
        return if (query.isNotEmpty()) {
            val url =
                baseUrl.toHttpUrl()
                    .newBuilder()
                    .addEncodedPathSegment("search")
                    .addQueryParameter("type", "1")
                    .addQueryParameter("searchString", query)
                    .toString()
            GET(url, headers)
        } else {
            val parts =
                filters.filterIsInstance<UriPartFilter>().joinToString("&") { it.toUriPart() }
            GET("$baseUrl/show?page=$page&$parts", headers)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        Log.i("ColaManga", "searchMangaParse: ${response.request.url}")
        val document = response.asJsoup()
        // Normal search
        return if (response.request.url.encodedPath.startsWith("/search")) {
            val mangas =
                document.select(searchMangaSelector()).map { element ->
                    searchMangaFromElement(element)
                }
            MangasPage(mangas, false)
            // Filter search
        } else {
            val mangas =
                document.select(popularMangaSelector()).map { element ->
                    popularMangaFromElement(element)
                }
            MangasPage(mangas, mangas.size == 36)
        }
    }

    override fun chapterListRequest(manga: SManga) = GET(baseUrl + manga.url, headers)

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)

    override fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)

    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            url = element.select("dt a").attr("href")
            title = element.select("h1").text().trim()
            thumbnail_url = element.select("a.fed-list-pics").attr("data-original")
        }
    }

    private fun mangaFromElement(element: Element): SManga {
        Log.i("ColaManga", "mangaFromElement: $element")
        val manga = SManga.create()
        manga.url = element.select("a.fed-list-title").attr("href")
        manga.title = element.select("a.fed-list-title").text().trim()
        manga.thumbnail_url = element.select("a.fed-list-pics").attr("data-original")
        return manga
    }

    override fun chapterFromElement(element: Element): SChapter {
        Log.i("ColaManga", "chapterFromElement: $element")
        return SChapter.create().apply {
            name = element.select("a.fed-btns-info").text().trim()
            url = element.select("a.fed-btns-info").attr("href")
        }
    }

    override fun mangaDetailsParse(document: Document): SManga {
        Log.i("ColaManga", "mangaDetailsParse: document")

        return SManga.create().apply {
            thumbnail_url =
                document.select("dt.fed-deta-images a.fed-list-pics").attr("data-original")
            description = document.select("p.fed-part-both").text().trim()
            title = document.select("h1").text().trim()
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        Log.i("Colamanga", "pageListParse: $document")
        val cDataRegex = """C_DATA='(.+)';""".toRegex()
        val cDataMatch = cDataRegex.find(document.toString())

        if (cDataMatch == null) {
            Log.e("Colamanga", "cDataMatch == null")
            return listOf()
        }

        val cData = cDataMatch.groupValues[1]
        val data = decryptor.decryptCData(cData)
        Log.i("Colamanga", "data: $data")
        val encCodeRegex =
            """enc_code1:\"(.+?)\".+enc_code2:\"(.+?)\".+domain:\"(.+?)\".+use_server:\"(.*?)\".+keyType:\"(.*?)\",imgKey:\"(.*?)\"""".toRegex()
        val encCodeMatch = encCodeRegex.find(data)

        if (encCodeMatch == null) {
            Log.e("ColaManga", "encCodeMatch == null")
            return listOf()
        }

        val encCode1 = encCodeMatch.groupValues[1]
        val encCode2 = encCodeMatch.groupValues[2]
        var domain = encCodeMatch.groupValues[3]
        val useServer = encCodeMatch.groupValues[4]
        val keyType = encCodeMatch.groupValues[5]
        val encImgKey = encCodeMatch.groupValues[6]

        if (useServer.isNotEmpty()) {
            domain = domain.replace("img", "img$useServer")
        }

        Log.i("Colamanga", "encCode1: $encCode1")
        Log.i("Colamanga", "encCode2: $encCode2")
        Log.i("Colamanga", "domain: $domain")
        Log.i("Colamanga", "useServer: $useServer")
        Log.i("Colamanga", "keyType: $keyType")
        Log.i("Colamanga", "encImgKey: $encImgKey")

        val pageNumber = decryptor.decryptPageNumber(encCode1)
        val pageUrl = decryptor.decryptPageUrl(encCode2)
        val imgKey = decryptor.getImgKey(keyType, encImgKey)

        Log.i("Colamanga", "pageNumber: $pageNumber")
        Log.i("Colamanga", "pageUrl: $pageUrl")
        Log.i("Colamanga", "imgKey: $imgKey")

        var finalUrl: String
        // 通过 queryParameter 传递 imgKey
        // 这样就算网址被缓存，也不会出现 imgKey 丢失的问题
        if (encImgKey.isEmpty()) {
            finalUrl = "https://$domain/comic/${pageUrl}0001.jpg?imgKey=$imgKey"
        } else {
            finalUrl = "https://$domain/comic/${pageUrl}0001.enc.webp?imgKey=$imgKey"
        }

        Log.i("ColaManga", "finalUrl: $finalUrl")

        // https://img.colamanga.com/comic/18735/SUwwSUdlUjZGeEQveU01SWd2enA2TEVpQ0MwMFpLa25kMmZOaEpyeEdpaz0=/0001.enc.webp
        return (1..pageNumber.toInt()).map {
            Page(it, "", finalUrl.replace("0001", it.toString().padStart(4, '0')))
        }
    }

    override fun imageUrlParse(document: Document) = ""

    override fun getFilterList() = getFilters()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        getPreferencesInternal(screen.context).forEach(screen::addPreference)
    }
}
