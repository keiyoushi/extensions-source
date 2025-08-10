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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater

private val json = Json { ignoreUnknownKeys = true }

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

    private val host = "www.scan-manga.com"
    override val baseUrl = "https://www.scan-manga.com"

    override val lang = "fr"

    private val urlNoImg = "https://i.postimg.cc/gcyWbCTk/NOIMAGE.png"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .cookieJar(SimpleCookieJar())
        .addNetworkInterceptor { chain ->
            val originalRequest = chain.request()
            val newRequest = originalRequest.newBuilder()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:141.0) Gecko/20100101 Firefox/141.0",
                )
                .header("Accept", "*/*")
                .header("Accept-Language", "fr,fr-FR;q=0.8,en-US;q=0.5,en;q=0.3")
                .header("Referer", originalRequest.header("Referer") ?: "")
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Origin", baseUrl)
                .header("source", originalRequest.header("source") ?: "")
                .header("Token", "yf")
                .header("Connection", "keep-alive")
                .build()
            chain.proceed(newRequest)
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/TOP-Manga-Webtoon-36.html", headers)
    }

    override fun popularMangaSelector() = "div.image_manga.image_listing"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            val linkElement = element.selectFirst("a[href]")!!
            val imgElement = element.selectFirst("img")!!

            title = imgElement.attr("title")
            setUrlWithoutDomain(linkElement.attr("href"))

            thumbnail_url = imgElement.attr("data-original")
                .takeIf { it.isNotBlank() }
                ?: imgElement.attr("src")
        }
    }

    override fun popularMangaNextPageSelector(): String? = null

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesSelector() = "div#content_news div.listing"

    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            val mangaElement = element.selectFirst("a.nom_manga")!!

            title = mangaElement.text()
            setUrlWithoutDomain(mangaElement.attr("href"))

            thumbnail_url = urlNoImg
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
        val urlRequest = "https://static.scan-manga.com/img/manga"
        val json = response.body.string()
        val jsonObj = JSONObject(json)

        val titlesArray = jsonObj.optJSONArray("title") ?: JSONArray()

        val mangas = mutableListOf<SManga>()

        for (i in 0 until titlesArray.length()) {
            val item = titlesArray.getJSONObject(i)

            val manga = SManga.create().apply {
                title = item.optString("nom_match", "")
                setUrlWithoutDomain(item.optString("url", ""))

                thumbnail_url = item.optString("image").let { "$urlRequest/$it" }
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

        val authors = document.select("div.contenu_texte_fiche_technique li[itemprop=author] a")
            .joinToString(", ") { it.text() }
        manga.author = authors

        manga.description = document.selectFirst("div.texte_synopsis_manga p[itemprop=description]")?.text()

        val genreLi = document.selectFirst("div.contenu_texte_fiche_technique li[itemprop=genre]")
        val genreSimple = genreLi?.text()?.takeIf { it.isNotBlank() } ?: ""

        val genreDetailsLi = genreLi?.nextElementSibling()
        val genreDetails = genreDetailsLi?.select("a")
            ?.map { a ->
                a.textNodes().joinToString(" ") { it.text().trim() }
            }
            ?.filter { it.isNotBlank() }
            ?.joinToString(", ") ?: ""

        manga.genre = listOf(genreSimple, genreDetails)
            .filter { it.isNotBlank() }
            .joinToString(", ")

        val statusText = document.select("div.contenu_texte_fiche_technique li")
            .firstOrNull { it.text().contains("En cours", ignoreCase = true) || it.text().contains("Terminé", ignoreCase = true) }
            ?.text()
        manga.status = when {
            statusText?.contains("En cours", ignoreCase = true) == true -> SManga.ONGOING
            statusText?.contains("Terminé", ignoreCase = true) == true -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        manga.thumbnail_url = document.selectFirst("div.image_manga img[itemprop=image]")?.attr("src")

        return manga
    }

    // chapter
    override fun chapterListSelector() = "div.volume_manga"

    override fun chapterFromElement(element: Element): SChapter {
        val linkEl = element.selectFirst("ul li.chapitre a[href]")!!
        val chapitreNomEl = element.selectFirst("ul li.chapitre div.chapitre_nom")

        val chapterName = chapitreNomEl?.text() ?: linkEl.text()

        return SChapter.create().apply {
            name = chapterName.trim()
            setUrlWithoutDomain(linkEl.attr("href"))
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()

        document.select(chapterListSelector()).forEach { volume ->
            val ul = volume.selectFirst("ul") ?: return@forEach
            ul.select("li.chapitre").forEach { li ->
                val linkEl = li.selectFirst("a[href]") ?: return@forEach
                val chapitreNomEl = li.selectFirst("div.chapitre_nom")

                val chapterName = chapitreNomEl?.text() ?: linkEl.text()

                chapters.add(
                    SChapter.create().apply {
                        name = chapterName.trim()
                        setUrlWithoutDomain(linkEl.attr("href"))
                    },
                )
            }
        }

        return chapters
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        // Decode
        val script = document.select("script")
            .mapNotNull { it.data() }
            .firstOrNull { it.contains("const idc") }!!

        val obfuscatedJs = script.trimIndent()

        val params = extractParams(obfuscatedJs)

        val deobfuscator = Deobfuscator(
            encoded = params.encoded,
            mask = params.mask,
            interval = params.interval,
            option = params.option,
        )

        val decodedJs = deobfuscator.decode()

        // Variables
        val sme = Regex("""sme\s*=\s*["'](.+?)["']""").find(decodedJs)?.groupValues?.get(1)
        val sml = Regex("""sml\s*=\s*["'](.+?)["']""").find(decodedJs)?.groupValues?.get(1)

        val idc = document.location()
            .substringAfterLast("_")
            .substringBefore(".html")

        val idcINT = idc.toInt()

        val chapterUrl = document.location()

        val postUrl = "$baseUrl/api/lel/$idc.json"

        // Request
        val client = network.cloudflareClient.newBuilder()
            .cookieJar(SimpleCookieJar())
            .build()

        val jsonBody = JSONObject().apply {
            put("a", sme)
            put("b", sml)
        }.toString()

        Log.d("ScanManga", "host: $host")
        Log.d("ScanManga", "chapiitre: $chapterUrl")

        Log.d("ScanManga", "baseUrl: $baseUrl")
        val headers = headersBuilder()
            .add("Host", host)
            .add("Referer", chapterUrl)
            .add("Content-Length", jsonBody.length.toString())
            .add("Origin", baseUrl)
            .add("Connection", "keep-alive")
            .add("Sec-Fetch-Dest", "empty")
            .add("Sec-Fetch-Mode", "cors")
            .add("Sec-Fetch-Site", "same-origin")
            .add("TE", "trailers")
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:141.0) Gecko/20100101 Firefox/141.0")
            .add("Accept", "*/*")
            .add("Accept-Language", "fr,fr-FR;q=0.8,en-US;q=0.5,en;q=0.3")
            .add("Content-Type", "application/json; charset=UTF-8")
            .add("source", chapterUrl)
            .add("Token", "yf")
            .add("Priority", "u=4")
            .build()

        val request = Request.Builder()
            .url(postUrl)
            .post(jsonBody.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .headers(headers)
            .build()

        val response = client.newCall(request).execute()

        val rawString = response.body.string()

        val pages = decodeDataAPIToPages(rawString, idcINT, document.location())

        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        Log.d("ScanManga", "page.url: ${page.url}")

        Log.d("ScanManga", "baseUrl: $baseUrl")
        val imgHeaders = Headers.Builder()
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:141.0) Gecko/20100101 Firefox/141.0")
            .add("Referer", page.url)
            .add("Origin", baseUrl)
            .add("Accept", "*/*")
            .add("Accept-Language", "fr,fr-FR;q=0.8,en-US;q=0.5,en;q=0.3")
            .add("Connection", "keep-alive")
            .build()

        return GET(page.imageUrl!!, imgHeaders)
    }
}

// Class and functions
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

fun decodeDataAPIToPages(chaine: String, idc: Int, chapterUrl: String): List<Page> {
    // 1. Base64 decode de D
    val step1 = Base64.decode(chaine, Base64.DEFAULT)

    // 2. Décompression zlib
    val inflater = Inflater()
    inflater.setInput(step1)
    val outputStream = ByteArrayOutputStream()
    val buffer = ByteArray(1024)
    while (!inflater.finished()) {
        val count = inflater.inflate(buffer)
        outputStream.write(buffer, 0, count)
    }
    inflater.end()
    var step2 = outputStream.toString(Charsets.UTF_8.name())

    // 3. Supprimer l'idc en hex à la fin
    val hexId = idc.toString(16)
    step2 = step2.replace(Regex(hexId + "$"), "")

    // 4. Inverser la chaîne
    val reversed = step2.reversed()

    // 5. Base64 decode à nouveau
    val step3 = Base64.decode(reversed, Base64.DEFAULT)

    // 6. Convertir en JSON
    val jsonString = String(step3, Charsets.UTF_8)

    val jsonObject = json.parseToJsonElement(jsonString).jsonObject

    val dN = jsonObject["dN"]!!.jsonPrimitive.content
    val s = jsonObject["s"]!!.jsonPrimitive.content
    val v = jsonObject["v"]!!.jsonPrimitive.content
    val c = jsonObject["c"]!!.jsonPrimitive.content
    val images = jsonObject["p"]!!.jsonObject

    return images.entries.map { (key, value) ->
        val obj = value.jsonObject
        val file = obj["f"]!!.jsonPrimitive.content
        val ext = obj["e"]!!.jsonPrimitive.content
        val imageUrl = "https://$dN/$s/$v/$c/$file.$ext"

        Page(
            index = key.toInt() - 1,
            url = chapterUrl,
            imageUrl = imageUrl,
        )
    }.sortedBy { it.index }
}
