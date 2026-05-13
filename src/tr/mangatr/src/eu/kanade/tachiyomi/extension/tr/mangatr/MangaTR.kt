package eu.kanade.tachiyomi.extension.tr.mangatr

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.util.Calendar

class MangaTR : ParsedHttpSource() {
    override val name = "Manga-TR"
    override val baseUrl = "https://manga-tr.com"
    override val lang = "tr"
    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(DDoSGuardInterceptor(network.cloudflareClient))
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
        .add("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7")

    private fun cleanUrl(href: String): String {
        val path = href.substringAfter(baseUrl).substringAfter("manga-tr.com").removePrefix("/")
        return "/$path"
    }

    // ===============================
    // Popular
    // ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/index.html", headers)

    override fun popularMangaSelector() = "li.sidebar-list__item:has(a.sidebar-list__link), div.media-card"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("a.sidebar-list__link, a.media-card__link, a")
        if (link != null) {
            url = cleanUrl(link.attr("href"))
        }
        title = element.select(".media-card__title, .sidebar-list__body strong, strong").text().trim()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector(): String? = null

    // ===============================
    // Search
    // ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/arama.html".toHttpUrl().newBuilder()
            .addQueryParameter("icerik", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaSelector() = "a.arama-manga-item"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        url = cleanUrl(element.attr("href"))
        title = element.selectFirst(".arama-manga-name")?.text()?.trim() ?: element.text().trim()
    }

    override fun searchMangaNextPageSelector(): String? = null

    // ===============================
    // Details
    // ===============================

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1")?.text()?.trim() ?: ""
        thumbnail_url = document.selectFirst("img.poster-card__image, img[src*='covers']")?.absUrl("src")
        description = document.selectFirst("div.detail-copy")?.text()?.trim()
        author = document.select("span.detail-meta-row__value:contains(Yazar), a[href*='author']").text().trim()
        genre = document.select("a[href*='tur=']").joinToString { it.text() }
    }

    // ===============================
    // Chapters
    // ===============================

    override fun chapterListSelector() = "article.chapter-card, div.chapter-item"

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val id = manga.url.substringAfter("manga-").substringBefore(".html").substringBefore("/")
        val requestUrl = "$baseUrl/cek/fetch_pages_manga.php?manga_cek=$id"
        val chapterHeaders = headersBuilder().add("X-Requested-With", "XMLHttpRequest").build()

        return client.newCall(GET(requestUrl, chapterHeaders))
            .asObservableSuccess()
            .map { response -> chapterListParse(response) }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var document = response.asJsoup()

        chapters.addAll(document.select(chapterListSelector()).map(::chapterFromElement))

        var nextPage = 2
        val requestUrl = response.request.url.toString()
        val chapterHeaders = headersBuilder().add("X-Requested-With", "XMLHttpRequest").build()

        while (document.selectFirst("a[data-page=$nextPage]") != null) {
            val body = FormBody.Builder().add("page", nextPage.toString()).build()
            val postResponse = client.newCall(POST(requestUrl, chapterHeaders, body)).execute()
            document = postResponse.asJsoup()
            chapters.addAll(document.select(chapterListSelector()).map(::chapterFromElement))
            nextPage++
        }

        return chapters
    }

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val link = element.selectFirst("a")
        if (link != null) {
            val href = link.attr("href")
            url = if (href.startsWith("http")) {
                href.substringAfter(baseUrl)
            } else {
                "/" + href.removePrefix("/")
            }
            name = link.text().trim()
        }
        val dateText = element.selectFirst("span.date, div.chapter-card__meta, .chapter-card__date")?.text() ?: ""
        date_upload = parseRelativeDate(dateText)
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrl = if (chapter.url.startsWith("http")) chapter.url else baseUrl + chapter.url
        return GET(chapterUrl, headers)
    }
    // ===============================
    // Decode
    // ===============================

    private fun decodeOrder(encoded: String): List<Pair<Int, Int>> {
        val list = mutableListOf<Pair<Int, Int>>()
        try {
            // Base64 şifresini çöz ve 0x5A XOR anahtarı ile kilidi aç
            val raw = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
            val decodedBytes = raw.map { (it.toInt() and 0xFF) xor 0x5A }.map { it.toByte() }.toByteArray()
            val jsonStr = String(decodedBytes, Charsets.UTF_8)

            // Çıkan sonucu JSON olarak oku ve sırayı listeye kaydet
            if (jsonStr.startsWith("[")) {
                val arr = org.json.JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val pos = arr.getString(i).toIntOrNull()
                    if (pos != null) list.add(Pair(i, pos))
                }
            } else if (jsonStr.startsWith("{")) {
                val obj = org.json.JSONObject(jsonStr)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val partIdx = key.toIntOrNull()
                    val pos = obj.getString(key).toIntOrNull()
                    if (partIdx != null && pos != null) list.add(Pair(partIdx, pos))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    // ===============================
    // Pages
    // ===============================

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("div.chapter-page").forEach { chapterPage ->
            val rawParts = chapterPage.attr("data-parts")
            val rawOrder = chapterPage.attr("data-order") // Şifreli sıralama haritası

            if (rawParts.isNotEmpty()) {
                try {
                    val cleanJson = rawParts.replace("&quot;", "\"")
                    val partsArray = org.json.JSONArray(cleanJson)
                    val partsMap = mutableMapOf<Int, String>()
                    for (i in 0 until partsArray.length()) {
                        var url = partsArray.getString(i)
                        url = url.replace("\\/", "/").replace("&amp;", "&")
                        partsMap[i] = url
                    }

                    if (rawOrder.isNotEmpty()) {
                        val orderList = decodeOrder(rawOrder)
                        val sortedOrder = orderList.sortedBy { it.second }

                        for (pair in sortedOrder) {
                            val partIdx = pair.first
                            val url = partsMap[partIdx]
                            if (url != null) {
                                pages.add(Page(pages.size, "", url))
                            }
                        }
                    } else {
                        for (i in 0 until partsMap.size) {
                            pages.add(Page(pages.size, "", partsMap[i]!!))
                        }
                    }
                } catch (e: Exception) {
                }
            }
        }

        if (pages.isEmpty()) {
            throw Exception("Resim bulunamadı! Sağ üstten 'WebView' simgesine tıklayıp Cloudflare/DDoS kontrolünü geçin.")
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String = ""

    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headersBuilder().add("Accept", "image/avif,image/webp,*/*").build())

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

    override fun getFilterList() = FilterList()
}
