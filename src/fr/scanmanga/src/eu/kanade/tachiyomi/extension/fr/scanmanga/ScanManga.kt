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

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .cookieJar(SimpleCookieJar())
        .addNetworkInterceptor { chain ->
            val originalRequest = chain.request()
            val newRequest = originalRequest.newBuilder()
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
            chain.proceed(newRequest)
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json: Json by injectLazy()
    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept-Language", "fr-FR")
        .set(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 16; LM-X420) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.7204.158 Mobile Safari/537.36",
        )

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
            name = if (extraTitle != null) "$chapterName - $extraTitle" else chapterName
            setUrlWithoutDomain(linkEl.absUrl("href"))
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).map { chapterFromElement(it) }
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        val script = document.select("script")
            .mapNotNull { it.data() }
            .firstOrNull { it.contains("const idc") }
            ?: throw Exception("Script contenant 'const idc' non trouvé")

        Log.d("MyExtension", "script : $script")

        val obfuscatedJs = script.trimIndent()
        val params = extractParams(obfuscatedJs)

        val deobfuscator = Deobfuscator(
            encoded = params.encoded,
            mask = params.mask,
            interval = params.interval,
            option = params.option,
        )

        val decodedJs = deobfuscator.decode()
        Log.d("MyExtension", "decodedJS : $decodedJs")

        val sme = Regex("""sme\s*=\s*["'](.+?)["']""").find(decodedJs)?.groupValues?.get(1)
            ?: error("Impossible d'extraire 'sme'")
        val sml = Regex("""sml\s*=\s*["'](.+?)["']""").find(decodedJs)?.groupValues?.get(1)
            ?: error("Impossible d'extraire 'sml'")

        Log.d("MyExtension", "a : $sme")
        Log.d("MyExtension", "b : $sml")

        val idc = document.location()
            .substringAfterLast("_")
            .substringBefore(".html")
        Log.d("MyExtension", "chapterID : $idc")

        Log.d("MyExtension", "baseUrl : $baseUrl")
        val chapterUrl = document.location()
        Log.d("MyExtension", "chapterURL : $chapterUrl")

        val postUrl = "$baseUrl/api/lel/$idc.json"
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

        val dataJson = decodeDataApi(responseBody, idc)
        Log.d("MyExtension", "dataJson : $dataJson")

        val server = dataJson.getString("dC")
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
        }.toList()
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
