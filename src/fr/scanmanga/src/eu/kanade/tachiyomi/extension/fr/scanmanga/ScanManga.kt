package eu.kanade.tachiyomi.extension.fr.scanmanga

import android.util.Base64
import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater

@Suppress("SpellCheckingInspection")
class ScanManga : ParsedHttpSource() {
    class SimpleCookieJar : CookieJar {
        private val cookieStore = mutableListOf<Cookie>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookieStore += cookies
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore
        }
    }

    override val name = "Scan-Manga"

    override val baseUrl = "https://m.scan-manga.com"

    override val lang = "fr"

    override val supportsLatest = true

    private val mobileUserAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Mobile Safari/537.36"

    val adbLoggingInterceptor = HttpLoggingInterceptor { message ->
        val chunkSize = 4011 // logcat can take a maximum of 4011 characters in the message
        val bins = mutableListOf<MutableList<String>>()
        var currentBin = mutableListOf<String>()
        bins += currentBin

        for (line in message.split(Regex("\r?\n"))) {
            var part = line

            do {
                if (part.length < chunkSize) {
                    currentBin.add(part)
                    part = ""
                } else {
                    // Start a new bin for large chunks
                    currentBin = mutableListOf()
                    bins += currentBin

                    currentBin.add(part.take(chunkSize))
                    part = part.drop(chunkSize)
                }
            } while (part.isNotEmpty())
        }

        for (bin in bins) {
            Log.v("ScanManga.OkHttpClient", bin.joinToString("\n"))
        }
    }.setLevel(HttpLoggingInterceptor.Level.BODY)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .cookieJar(SimpleCookieJar())
        .addNetworkInterceptor { chain ->
            val newReq = chain.request().newBuilder()
                .header("User-Agent", mobileUserAgent)
                .build()
            chain.proceed(newReq)
        }.addInterceptor(
            adbLoggingInterceptor,
        )
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept-Language", "fr,fr-FR;q=0.8,en-US;q=0.5,en;q=0.3")

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/TOP-Manga-Webtoon-36.html", headers)
    }

    override fun popularMangaSelector() = "#carouselTOPContainer > div.top"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            val titleElement = element.selectFirst("a.atop")!!

            title = titleElement.text()
            setUrlWithoutDomain(titleElement.attr("href"))
            thumbnail_url = element.selectFirst("img")?.attr("data-original")
        }
    }

    override fun popularMangaNextPageSelector(): String? = null

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesSelector() = "#content_news .publi"

    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            val mangaElement = element.selectFirst("a.l_manga")!!

            title = mangaElement.text()
            setUrlWithoutDomain(mangaElement.attr("href"))

            thumbnail_url = element.selectFirst("img")?.attr("src")
        }
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/search/quick.json"
            .toHttpUrl().newBuilder()
            .addQueryParameter("term", query)
            .build()
            .toString()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val json = response.body.string()
        val jsonObj = JSONObject(json)

        val titlesArray = jsonObj.optJSONArray("title") ?: JSONArray()

        val mangas = mutableListOf<SManga>()

        for (i in 0 until titlesArray.length()) {
            val item = titlesArray.getJSONObject(i)

            val manga = SManga.create().apply {
                title = item.optString("nom_match", "Titre inconnu")

                setUrlWithoutDomain(item.optString("url", ""))

                thumbnail_url = item.optString("image", "").let { "https://static.scan-manga.com/img/manga/$it" }
            }
            mangas.add(manga)
        }

        return MangasPage(mangas, false)
    }

    override fun searchMangaSelector() = throw UnsupportedOperationException("Not used")
    override fun searchMangaFromElement(element: Element) = throw UnsupportedOperationException("Not used")
    override fun searchMangaNextPageSelector() = null

    // Details
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()

        manga.title = document.select("h1.main_title[itemprop=name]").text()
        manga.author = document.select("div[itemprop=author]").text()
        manga.description = document.selectFirst("div.titres_desc[itemprop=description]")?.text()
        manga.genre = document.selectFirst("div.titres_souspart span[itemprop=genre]")?.text()

        val statutText = document.selectFirst("div.titres_souspart")?.ownText()
        manga.status = when {
            statutText?.contains("En cours", ignoreCase = true) == true -> SManga.ONGOING
            statutText?.contains("Terminé", ignoreCase = true) == true -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        // TODO: Check this
        manga.thumbnail_url = document.select("div.full_img_serie img[itemprop=image]").attr("src")

        return manga
    }

    // Chapters
    override fun chapterListSelector() = "div.chapt_m"

    override fun chapterFromElement(element: Element): SChapter {
        val linkEl = element.selectFirst("td.publimg span.i a")!!
        val titleEl = element.selectFirst("td.publititle")

        val chapterName = linkEl.text()
        val extraTitle = titleEl?.text()

        return SChapter.create().apply {
            name = if (!extraTitle.isNullOrEmpty()) "$chapterName - $extraTitle" else chapterName
            setUrlWithoutDomain(linkEl.absUrl("href"))
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).map { chapterFromElement(it) }
    }

    // Pages
    private fun decodeHunter(obfuscatedJs: String): String {
        val regex = Regex("""eval\(function\(h,u,n,t,e,r\)\{.*?\}\("([^"]+)",\d+,"([^"]+)",(\d+),(\d+),\d+\)\)""")
        val (encoded, mask, intervalStr, optionStr) = regex.find(obfuscatedJs)?.destructured
            ?: error("Failed to match obfuscation pattern: $obfuscatedJs")

        val interval = intervalStr.toInt()
        val option = optionStr.toInt()
        val delimiter = mask[option]
        val tokens = encoded.split(delimiter).filter { it.isNotEmpty() }
        val reversedMap = mask.withIndex().associate { it.value to it.index }

        return buildString {
            for (token in tokens) {
                // Reverse the hashIt() operation: convert masked characters back to digits
                val digitString = token.map { c ->
                    reversedMap[c]?.toString() ?: error("Invalid masked character: $c")
                }.joinToString("")

                // Convert from base `option` to decimal
                val number = digitString.toIntOrNull(option)
                    ?: error("Failed to parse token: $digitString as base $option")

                // Reverse the shift done during encodeIt()
                val originalCharCode = number - interval

                append(originalCharCode.toChar())
            }
        }
    }

    private fun dataAPI(data: String, idc: Int): UrlPayload {
        // Step 1: Base64 decode the input
        val compressedBytes = Base64.decode(data, Base64.DEFAULT)

        // Step 2: Inflate (zlib decompress)
        val inflater = Inflater()
        inflater.setInput(compressedBytes)
        val outputBuffer = ByteArray(512 * 1024) // 512 KB buffer, should be more than enough
        val decompressedLength = inflater.inflate(outputBuffer)
        inflater.end()

        val inflated = String(outputBuffer, 0, decompressedLength)

        // Step 3: Remove trailing hex string and reverse
        val hexIdc = idc.toString(16)
        val cleaned = inflated.replace(Regex("$hexIdc$"), "")
        val reversed = cleaned.reversed()

        // Step 4: Base64 decode and parse JSON
        val finalJsonStr = String(Base64.decode(reversed, Base64.DEFAULT))

        return finalJsonStr.parseAs<UrlPayload>()
    }

    /*
        override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val packedScript = document.selectFirst("script:containsData(h,u,n,t,e,r)")!!.data()

        val unpackedScript = decodeHunter(packedScript)
        val parametersRegex = Regex("""sml = '([^']+)';\n.*var sme = '([^']+)'""")

        val (sml, sme) = parametersRegex.find(unpackedScript)!!.destructured

        Log.d(
            "ScanManga",
            JSONObject()
                .put("a (sme)", sme)
                .put("b (sml)", sml)
                .toString(),
        )

        if (sml == "SWIHHAJjEwpkYWkLbg==") { error("(╯°□°)╯︵ ┻━┻  HOW DO THEY DETECT ME") }

        val chapterInfoRegex = Regex("""const idc = (\d+)""")
        val (chapterId) = chapterInfoRegex.find(packedScript)!!.destructured

        val MEDIA_TYPE = "application/json; charset=UTF-8".toMediaType()
        val requestBody = """{"a":"$sme","b":"$sml"}"""

        val chapterListRequest = Request.Builder()
            .url("$baseUrl/api/lel/$chapterId.json")
            .post(requestBody.toRequestBody(MEDIA_TYPE))
            .headers(
                headersBuilder()
                    .set("source", "${response.request.url}")
                    .set("Token", "yf")
                    .set("Origin", "https://m.scan-manga.com")
                    .build(),
            )
            .build()

        val lelResponse = client.newCall(chapterListRequest).execute().use { response ->
            if (!response.isSuccessful) { error("Unexpected error while fetching lel.") }
            dataAPI(response.body.toString(), chapterId.toInt())
        }

        return lelResponse.generateImageUrls().map { Page(it.key, it.value) }
    }
     */

    override fun pageListParse(document: Document): List<Page> {
        val packedScript = document.selectFirst("script:containsData(h,u,n,t,e,r)")!!.data()

        val unpackedScript = decodeHunter(packedScript)
        val parametersRegex = Regex("""sml = '([^']+)';\n.*var sme = '([^']+)'""")

        val (sml, sme) = parametersRegex.find(unpackedScript)!!.destructured

        Log.d(
            "ScanManga",
            JSONObject()
                .put("a (sme)", sme)
                .put("b (sml)", sml)
                .toString(),
        )

        val chapterInfoRegex = Regex("""const idc = (\d+)""")
        val (chapterId) = chapterInfoRegex.find(packedScript)!!.destructured

        val mediaType = "application/json; charset=UTF-8".toMediaType()
        val requestBody = """{"a":"$sme","b":"$sml"}"""

        val documentUrl = document.baseUri().toHttpUrl()

        val chapterListRequest = Request.Builder()
            .url("$baseUrl/api/lel/$chapterId.json")
            .post(requestBody.toRequestBody(mediaType))
            .header("User-Agent", mobileUserAgent)
            .header("Accept-Language", "fr")
            .header("Referer", documentUrl.toString())
            .header("Origin", "${documentUrl.scheme}://${documentUrl.host}")
            .header("Token", "yf")
            .build()

        val lelResponse = client.newBuilder().cookieJar(SimpleCookieJar()).build()
            .newCall(chapterListRequest).execute().use { response ->
                if (!response.isSuccessful) { error("Unexpected error while fetching lel.") }
                dataAPI(response.body.toString(), chapterId.toInt())
            }

        val pages = lelResponse.generateImageUrls().map { Page(it.key, it.value) }
        pages.forEach { page -> Log.d("ScanManga", page.toString()) }
        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imgHeaders = Headers.Builder()
            .add("User-Agent", mobileUserAgent)
            .add("Referer", page.url)
            .add("Origin", baseUrl)
            .add("Accept", "*/*")
            .add("Accept-Language", "fr,fr-FR;q=0.8,en-US;q=0.5,en;q=0.3")
            .add("Connection", "keep-alive")
            .build()

        return GET(page.imageUrl!!, imgHeaders)
    }
}
