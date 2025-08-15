package eu.kanade.tachiyomi.extension.fr.scanmanga

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
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
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
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

    private val mobileUserAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Mobile Safari/537.36"

    override val client: OkHttpClient = network.client.newBuilder()
        .cookieJar(SimpleCookieJar())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept-Language", "fr,fr-FR;q=0.8,en-US;q=0.5,en;q=0.3")
        .set("User-Agent", mobileUserAgent)

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
    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

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

        val newHeaders = headers.newBuilder()
            .add("Content-type", "application/json; charset=UTF-8")
            .build()

        return GET(url, newHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val json = response.body.string()
        if (json == "[]") { return MangasPage(emptyList(), false) }

        return MangasPage(
            json.parseAs<MangaSearchDto>().title?.map {
                SManga.create().apply {
                    title = it.nom_match ?: "Titre inconnu"
                    setUrlWithoutDomain(it.url!!)
                    thumbnail_url = "https://static.scan-manga.com/img/manga/${it.image}"
                }
            } ?: emptyList(),
            false,
        )
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
        val compressedBytes = Base64.decode(data, Base64.NO_WRAP or Base64.NO_PADDING)

        // Step 2: Inflate (zlib decompress)
        val inflater = Inflater()
        inflater.setInput(compressedBytes)
        val outputBuffer = ByteArray(512 * 1024) // 512 KB buffer, should be more than enough
        val decompressedLength = inflater.inflate(outputBuffer)
        inflater.end()

        val inflated = String(outputBuffer, 0, decompressedLength)

        // Step 3: Remove trailing hex string and reverse
        val hexIdc = idc.toString(16)
        val cleaned = inflated.removeSuffix(hexIdc)
        val reversed = cleaned.reversed()

        // Step 4: Base64 decode and parse JSON
        val finalJsonStr = String(Base64.decode(reversed, Base64.DEFAULT))

        return finalJsonStr.parseAs<UrlPayload>()
    }

    override fun pageListParse(document: Document): List<Page> {
        val packedScript = document.selectFirst("script:containsData(h,u,n,t,e,r)")!!.data()

        val unpackedScript = decodeHunter(packedScript)
        val parametersRegex = Regex("""sml = '([^']+)';\n.*var sme = '([^']+)'""")

        val (sml, sme) = parametersRegex.find(unpackedScript)!!.destructured

        val chapterInfoRegex = Regex("""const idc = (\d+)""")
        val (chapterId) = chapterInfoRegex.find(packedScript)!!.destructured

        val mediaType = "application/json; charset=UTF-8".toMediaType()
        val requestBody = """{"a":"$sme","b":"$sml"}"""

        val documentUrl = document.baseUri().toHttpUrl()

        val pageListRequest = POST(
            "$baseUrl/api/lel/$chapterId.json",
            headers.newBuilder()
                .add("Origin", "${documentUrl.scheme}://${documentUrl.host}")
                .add("Referer", documentUrl.toString())
                .add("Token", "yf")
                .build(),
            requestBody.toRequestBody(mediaType),
        )

        val lelResponse = client.newBuilder().cookieJar(SimpleCookieJar()).build()
            .newCall(pageListRequest).execute().use { response ->
                if (!response.isSuccessful) { error("Unexpected error while fetching lel.") }
                dataAPI(response.body.string(), chapterId.toInt())
            }

        return lelResponse.generateImageUrls().map { Page(it.key, imageUrl = it.value) }
    }

    // Page
    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headers.newBuilder()
            .add("Origin", baseUrl)
            .build()

        return GET(page.imageUrl!!, imgHeaders)
    }
}
