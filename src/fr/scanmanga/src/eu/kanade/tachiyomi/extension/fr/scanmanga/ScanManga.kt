package eu.kanade.tachiyomi.extension.fr.scanmanga

import android.util.Base64
import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.util.zip.Inflater

class ScanManga : ParsedHttpSource() {

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
        .addNetworkInterceptor { chain ->
            val domain = chain.request().url
            val newUA = if (isMobile(domain)) mobileUserAgent else desktopUserAgent
            val newReq = chain
                .request()
                .newBuilder()
                .header("User-Agent", newUA)
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
        .build()

    private val json: Json by injectLazy()
    private fun shuffle(s: String): String {
        val chars = s.toMutableList()
        chars.shuffle()
        return chars.joinToString("")
    }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept-Language", "fr,fr-FR;q=0.8,en-US;q=0.5,en;q=0.3")

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/TOP-Manga-Webtoon-36.html", headers)
    }

    override fun popularMangaSelector() = "div.top:has(a.atop)"

    override fun popularMangaFromElement(element: Element): SManga {
        if (element.html().isEmpty()) {
            error("WTF")
        }
        val manga = SManga.create()

        val titleElement = element.selectFirst("a.atop")
        val imgElement = element.selectFirst("a > img")

        manga.setUrlWithoutDomain(titleElement!!.absUrl("href"))
        manga.title = titleElement.text()
        manga.thumbnail_url = imgElement?.absUrl("data-original")
        return manga
    }

    override fun popularMangaNextPageSelector(): String? = null

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesSelector() = "#content_news .listing"

    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("a.nom_manga").text()
            setUrlWithoutDomain(element.select("a.nom_manga").attr("href"))
        }
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = desktopBaseUrl.toHttpUrl().newBuilder()
            .addPathSegments("scanlation/liste_series.html")
            .addQueryParameter("q", query)
            .build()
            .toString()

        return GET(
            url,
            headersBuilder()
                .add("Referer", "$desktopBaseUrl/")
                .build(),
        )
    }

    override fun searchMangaSelector() = "div.texte_manga.book_close, div.texte_manga.book_stop"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val link = element.selectFirst("a.texte_manga")!!

        val path = link.attr("href")
        // Construction correcte de l'URL vers la version mobile
        manga.setUrlWithoutDomain(path)
        manga.url = if (path.startsWith("/")) "https://m.scan-manga.com$path" else path

        manga.title = link.text().trim()
        return manga
    }

    override fun searchMangaNextPageSelector(): String? = null

    // Details
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()

        manga.title = document.select("h1.main_title[itemprop=name]").text()
        manga.author = document.select("div[itemprop=author]").text()
        manga.description = document.select("div.titres_desc[itemprop=description]").text()
        manga.genre = document.select("div.titres_souspart span[itemprop=genre]").joinToString { it.text() }

        val statutText = document.select("div.titres_souspart").firstOrNull { it.text().contains("Statut") }?.ownText()
        manga.status = when {
            statutText?.contains("En cours", ignoreCase = true) == true -> SManga.ONGOING
            statutText?.contains("Terminé", ignoreCase = true) == true -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        // manga.thumbnail_url = document.selectFirst("div.full_img_serie img[itemprop=image]")?.absUrl("src")

        return manga
    }

    // Chapters
    override fun chapterListSelector() = "div.chapt_m"

    override fun chapterFromElement(element: Element): SChapter {
        val linkEl = element.selectFirst("td.publimg span.i a")!!
        val titleEl = element.selectFirst("td.publititle")

        val chapterName = linkEl.text()
        val extraTitle = titleEl?.text().orEmpty()

        return SChapter.create().apply {
            name = if (extraTitle.isNotEmpty()) "$chapterName - $extraTitle" else chapterName
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
        override fun pageListRequest(chapter: SChapter): Request {
            val req = GET(baseUrl + chapter.url, headers)
            Log.d("ScanManga", "${req.url}")
            Log.d("ScanManga", req.headers.joinToString("\n") { "${it.first}: ${it.second}" })
            return req
        }
     */

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

    override fun pageListParse(document: Document): List<Page> {
        throw NotImplementedError("Stub!")
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder()
            .add("Origin", page.url.toHttpUrl().host) // This is data.scan-manga.fr but it's ok, it's working
            .build()

        return GET(page.imageUrl!!, imgHeaders)
    }
}
