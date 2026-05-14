package eu.kanade.tachiyomi.extension.tr.mangatr

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.util.Calendar

class MangaTR : HttpSource() {
    override val name = "Manga-TR"
    override val baseUrl = "https://manga-tr.com"
    override val lang = "tr"
    override val supportsLatest = false // Guideline Issue 11

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(DDoSGuardInterceptor(network.cloudflareClient))
        .rateLimit(2)
        .build()

    private val json: Json by injectLazy()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
        .add("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7")
    // Intentionally removed User-Agent to let Mihon use the default WebView User-Agent.

    private val imageHeaders by lazy {
        // Guideline Issue 10
        headersBuilder().add("Accept", "image/avif,image/webp,*/*").build()
    }

    // --- 1. POPULAR ---
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/index.html", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("li.sidebar-list__item:has(a.sidebar-list__link), div.media-card").map { element ->
            SManga.create().apply {
                val link = element.selectFirst("a.sidebar-list__link, a.media-card__link, a")
                if (link != null) {
                    setUrlWithoutDomain(link.absUrl("href")) // Guideline Issue 9
                }
                title = element.select(".media-card__title, .sidebar-list__body strong, strong").text() // Guideline Issue 5
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, false)
    }

    // --- 2. LATEST (Disabled per Guideline Issue 11) ---
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException("Not used.")
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used.")

    // --- 3. SEARCH ---
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/arama.html".toHttpUrl().newBuilder()
            .addQueryParameter("icerik", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("a.arama-manga-item").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href")) // Guideline Issue 9
                title = element.selectFirst(".arama-manga-name")?.text() ?: element.text() // Guideline Issue 5
            }
        }
        return MangasPage(mangas, false)
    }

    // --- 4. DETAILS ---
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1")?.text() ?: "" // Guideline Issue 5
            thumbnail_url = document.selectFirst("img.poster-card__image, img[src*='covers']")?.absUrl("src")
            description = document.selectFirst("div.detail-copy")?.text() // Guideline Issue 5
            author = document.select("span.detail-meta-row__value:contains(Yazar), a[href*='author']").text() // Guideline Issue 5
            genre = document.select("a[href*='tur=']").joinToString { it.text() } // Guideline Issue 5
        }
    }

    // --- 5. CHAPTERS ---
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        // Unutma ki manga url'sinden ID'yi çekiyoruz (Örn: manga-vagabond -> id)
        val id = manga.url.substringAfter("manga-").substringBefore(".html").substringBefore("/")
        val requestUrl = "$baseUrl/cek/fetch_pages_manga.php?manga_cek=$id"
        val chapterHeaders = headersBuilder().add("X-Requested-With", "XMLHttpRequest").build()

        val chapters = mutableListOf<SChapter>()
        var nextPage = 1
        var hasNext = true

        while (hasNext) {
            val request = if (nextPage == 1) {
                GET(requestUrl, chapterHeaders)
            } else {
                val body = FormBody.Builder().add("page", nextPage.toString()).build()
                POST(requestUrl, chapterHeaders, body)
            }

            client.newCall(request).execute().use { response ->
                // Guideline Issue 13
                val document = response.asJsoup()
                chapters.addAll(
                    document.select("article.chapter-card, div.chapter-item").map { element ->
                        SChapter.create().apply {
                            val link = element.selectFirst("a")
                            if (link != null) {
                                val href = link.attr("href")
                                // "/cek/" klasör tuzağını aşmak için URL'yi ana domain ile zorla birleştiriyoruz
                                val absoluteUrl = if (href.startsWith("http")) href else "$baseUrl/${href.substringAfterLast("/")}"
                                setUrlWithoutDomain(absoluteUrl) // Guideline Issue 9
                                name = link.text() // Guideline Issue 5
                            }
                            val dateText = element.selectFirst("span.date, div.chapter-card__meta, .chapter-card__date")?.text() ?: "" // Guideline Issue 5
                            date_upload = parseRelativeDate(dateText)
                        }
                    },
                )
                nextPage++
                hasNext = document.selectFirst("a[data-page=$nextPage]") != null
            }
        }
        chapters
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException("Not used.")

    // --- 6. PAGES ---
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrl = if (chapter.url.startsWith("http")) chapter.url else baseUrl + chapter.url
        return GET(chapterUrl, headers)
    }

    // Decrypts the map to arrange chapter pages correctly
    private fun decodeOrder(encoded: String): List<Pair<Int, Int>> {
        val list = mutableListOf<Pair<Int, Int>>()
        val raw = Base64.decode(encoded, Base64.DEFAULT)
        val decodedBytes = raw.map { (it.toInt() and 0xFF) xor 0x5A }.map { it.toByte() }.toByteArray()
        val jsonStr = String(decodedBytes, Charsets.UTF_8).trimEnd('\u0000').trim()

        val jsonElement = json.parseToJsonElement(jsonStr) // Guideline Issue 12

        if (jsonStr.startsWith("[")) {
            jsonElement.jsonArray.forEachIndexed { index, element ->
                val pos = element.jsonPrimitive.intOrNull
                if (pos != null) list.add(Pair(index, pos))
            }
        } else if (jsonStr.startsWith("{")) {
            jsonElement.jsonObject.forEach { (key, element) ->
                val partIdx = key.toIntOrNull()
                val pos = element.jsonPrimitive.intOrNull
                if (partIdx != null && pos != null) list.add(Pair(partIdx, pos))
            }
        }
        return list
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pages = mutableListOf<Page>()

        document.select("div.chapter-page").forEach { chapterPage ->
            val rawParts = chapterPage.attr("data-parts")
            val rawOrder = chapterPage.attr("data-order") // Encrypted map

            if (rawParts.isNotEmpty()) {
                val cleanJson = rawParts.replace("&quot;", "\"")
                val partsArray = json.parseToJsonElement(cleanJson).jsonArray // Guideline Issue 12

                val partsMap = mutableMapOf<Int, String>()
                partsArray.forEachIndexed { index, element ->
                    var url = element.jsonPrimitive.content
                    url = url.replace("\\/", "/").replace("&amp;", "&")
                    partsMap[index] = url
                }

                if (rawOrder.isNotEmpty()) {
                    val orderList = decodeOrder(rawOrder)
                    val sortedOrder = orderList.sortedBy { it.second }

                    for (pair in sortedOrder) {
                        val partIdx = pair.first
                        val url = partsMap[partIdx]
                        if (url != null) {
                            pages.add(Page(pages.size, imageUrl = url)) // Guideline Issue 4
                        }
                    }
                } else {
                    for (i in 0 until partsMap.size) {
                        if (partsMap[i] != null) pages.add(Page(pages.size, imageUrl = partsMap[i]!!)) // Guideline Issue 4
                    }
                }
            }
        }

        if (pages.isEmpty()) {
            throw Exception("Page list is empty. Please open in WebView to bypass the security check.")
        }

        return pages
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used.") // Guideline Issue 3

    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, imageHeaders) // Guideline Issue 10

    private fun parseRelativeDate(date: String): Long {
        val calendar = Calendar.getInstance()
        val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
        when {
            date.contains("dakika") -> calendar.add(Calendar.MINUTE, -number)
            date.contains("saat") -> calendar.add(Calendar.HOUR, -number)
            date.contains("gün") -> calendar.add(Calendar.DAY_OF_MONTH, -number)
            date.contains("hafta") -> calendar.add(Calendar.WEEK_OF_MONTH, -number)
            date.contains("ay") -> calendar.add(Calendar.MONTH, -number)
            date.contains("yıl") -> calendar.add(Calendar.YEAR, -number)
        }
        return calendar.timeInMillis
    }
}
