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
import kotlinx.serialization.json.Json
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
import uy.kohesive.injekt.injectLazy
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater

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

    private val desktopBaseUrl = "https://www.scan-manga.com" // Desktop URL for search
    private val mobileUserAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Mobile Safari/537.36"
    private val desktopUserAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"

    private fun isMobile(url: HttpUrl): Boolean {
        return url.host.startsWith("m.")
    }

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .cookieJar(SimpleCookieJar())
        .addNetworkInterceptor { chain ->
            val originalRequest = chain.request()
            val domain = originalRequest.url
            val newUA = if (isMobile(domain)) mobileUserAgent else desktopUserAgent
            val newReq = chain
                .request()
                .newBuilder()
                .header("User-Agent", newUA)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 11; SAMSUNG SM-G973U) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/14.2 Chrome/87.0.4280.141 Mobile Safari/537.36")
                .header("Accept", "*/*")
                .header("Accept-Language", "fr,fr-FR;q=0.8,en-US;q=0.5,en;q=0.3")
                .header("Referer", originalRequest.header("Referer") ?: "")
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Origin", "https://m.scan-manga.com")
                .header("source", originalRequest.header("source") ?: "")
                .header("Token", "yf")
                .header("Connection", "keep-alive")
                .build()
            chain.proceed(newReq)
        }.addInterceptor(
            HttpLoggingInterceptor { message: String ->
                val bins = mutableListOf<MutableList<String>>()
                var currentBin = mutableListOf<String>()
                bins += currentBin

                for (line_ in message.splitToSequence(Regex("\r?\n"))) {
                    var line = line_

                    do {
                        if (line.length < 4011) {
                            currentBin.add(line)
                            line = ""
                        } else {
                            // Start new bin
                            currentBin = mutableListOf()
                            bins += currentBin

                            // Add chunk to new bin
                            currentBin.add(line.substring(0, 4011))
                            line = line.substring(4011)
                        }
                    } while (line.isNotEmpty())
                }

                for (bin in bins) {
                    Log.v("ScanManga.OkHttpClient", bin.joinToString("\n"))
                }
            }.setLevel(HttpLoggingInterceptor.Level.BODY),
        )
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json: Json by injectLazy()
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
        val json = response.body!!.string()
        val jsonObj = JSONObject(json)

        val titlesArray = jsonObj.optJSONArray("title") ?: JSONArray()

        val mangas = mutableListOf<SManga>()

        for (i in 0 until titlesArray.length()) {
            val item = titlesArray.getJSONObject(i)

            val manga = SManga.create().apply {
                title = item.optString("nom_match", "Titre inconnu")

                setUrlWithoutDomain(item.optString("url", ""))

                thumbnail_url = item.optString("image", null)?.let { "https://static.scan-manga.com/img/manga/$it" }
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
        val script = document.selectFirst("script:containsData(const idc)")!!.data()

        Log.d("MyExtension", "script : $script")

        val obfuscatedJs = script.trimIndent()
        val decodedJs = decodeHunter(obfuscatedJs)
        Log.d("MyExtension", "decodedJS : $decodedJs")

        val sme = Regex("""sme\s*=\s*["'](.+?)["']""").find(decodedJs)?.groupValues?.get(1)
            ?: error("Impossible d'extraire 'sme'")
        val sml = Regex("""sml\s*=\s*["'](.+?)["']""").find(decodedJs)?.groupValues?.get(1)
            ?: error("Impossible d'extraire 'sml'")

        Log.d("MyExtension", "a : $sme")
        Log.d("MyExtension", "b : $sml")

        val chapterInfoRegex = Regex("""const idc = (\d+)""")
        val (chapterId) = chapterInfoRegex.find(obfuscatedJs)!!.destructured
        Log.d("MyExtension", "chapterID : $chapterId")

        Log.d("MyExtension", "baseUrl : $baseUrl")
        val chapterUrl = document.location()
        Log.d("MyExtension", "chapterURL : $chapterUrl")

        val postUrl = "$baseUrl/api/lel/$chapterId.json"
        Log.d("MyExtension", "postUrl : $postUrl")

        val client = network.cloudflareClient.newBuilder()
            .cookieJar(SimpleCookieJar())
            .build()

        Log.d("MyExtension", "client : $client")

        val jsonBody = """
        {
        "a": "$sme",
        "b": "$sml"
        }
        """.trimIndent()
        Log.d("MyExtension", "jsonBody : $jsonBody")

        val mediaType = "application/json; charset=UTF-8".toMediaType()
        val body = jsonBody.toRequestBody(mediaType)
        Log.d("MyExtension", "mediaType : $mediaType")
        Log.d("MyExtension", "body : $body")

        val request = Request.Builder()
            .url(postUrl)
            .post(body)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 11; SAMSUNG SM-G973U) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/14.2 Chrome/87.0.4280.141 Mobile Safari/537.36")
            .header("Accept", "*/*")
            .header("Accept-Language", "fr,fr-FR;q=0.8,en-US;q=0.5,en;q=0.3")
            .header("Content-Type", "application/json; charset=UTF-8")
            .header("source", chapterUrl)
            .header("Token", "yf")
            .header("Referer", chapterUrl)
            // Option:
            .header("Sec-Fetch-Dest", "empty")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Site", "same-origin")
            .header("Priority", "u=4")
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body.string()
        Log.e("MyExtension", "response : $response")
        Log.e("MyExtension", "responseBody : $responseBody")

        val dataJson = dataAPI(responseBody, chapterId.toInt())
        Log.d("MyExtension", "dataJson : $dataJson")

        return dataJson.generateImageUrls().map { Page(it.key, chapterUrl, it.value) }

        /* val server = dataJson.getString("dC")
        val path = dataJson.getString("s")
        val images = dataJson.getJSONObject("p")
        Log.d("MyExtension", "dataImages : $images")

        return images.keys().asSequence().map { key ->
            val obj = images.getJSONObject(key)
            val file = obj.getString("f")
            val ext = obj.getString("e")
            val imageUrl = "https://$server/$path/$file.$ext"
            Log.d("MyExtension", "page : $imageUrl")
            Page(
                index = key.toInt() - 1,
                url = chapterUrl,
                imageUrl = imageUrl,
            )
        }.toList()*/
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imgHeaders = Headers.Builder()
            .add("User-Agent", "Mozilla/5.0 (Linux; Android 11; SAMSUNG SM-G973U) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/14.2 Chrome/87.0.4280.141 Mobile Safari/537.36")
            .add("Referer", page.url)
            .add("Origin", "$baseUrl")
            .add("Accept", "*/*")
            .add("Accept-Language", "fr,fr-FR;q=0.8,en-US;q=0.5,en;q=0.3")
            .add("Connection", "keep-alive")
            .build()

        return GET(page.imageUrl!!, imgHeaders)
    }
}

class Deobfuscator(
    private val encoded: String,
    private val mask: String,
    private val interval: Int,
    private val option: Int,
) {

    fun decode(): String {
        val delimiter = mask[option]
        val tokens = encoded.split(delimiter).filter { it.isNotEmpty() }
        val reversedMap = mask.withIndex().associateBy({ it.value }, { it.index })

        val result = StringBuilder()

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

            result.append(originalCharCode.toChar())
        }

        return result.toString()
    }
}

data class ObfuscationParams(
    val encoded: String,
    val mask: String,
    val interval: Int,
    val option: Int,
)

fun extractParams(obfuscatedJs: String): ObfuscationParams {
    val regex = Regex("""eval\(function\(h,u,n,t,e,r\)\{.*?\}\("([^"]+)",\d+,"([^"]+)",(\d+),(\d+),\d+\)\)""")

    val match = regex.find(obfuscatedJs) ?: error("Failed to match obfuscation pattern")

    val (encoded, mask, intervalStr, optionStr) = match.destructured

    return ObfuscationParams(
        encoded = encoded,
        mask = mask,
        interval = intervalStr.toInt(),
        option = optionStr.toInt(),
    )
}

fun decodeDataApi(D: String, idc: String): JSONObject {
    val decodedBytes = Base64.decode(D, Base64.DEFAULT)


    val inflater = Inflater(true)
    inflater.setInput(decodedBytes)

    val outputStream = ByteArrayOutputStream()
    val buffer = ByteArray(1024)
    while (!inflater.finished()) {
        val count = inflater.inflate(buffer)
        outputStream.write(buffer, 0, count)
    }
    inflater.end()

    val inflated = outputStream.toString("UTF-8")
    outputStream.close()


    val idcHex = idc.toInt().toString(16)
    val trimmed = inflated.removeSuffix(idcHex)


    val reversed = trimmed.reversed()

    // Base64 decode
    val finalJsonString = String(Base64.decode(reversed, Base64.DEFAULT))

    return JSONObject(finalJsonString)
}
