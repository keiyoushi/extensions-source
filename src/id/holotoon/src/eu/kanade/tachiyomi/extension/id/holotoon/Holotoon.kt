package eu.kanade.tachiyomi.extension.id.holotoon

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.jsoup.nodes.Element
import java.util.Calendar

class Holotoon : HttpSource() {

    override val name = "Holotoon"

    override val baseUrl = "https://v1.holotoon.site"

    override val lang = "id"

    override val supportsLatest = true

    // Bump versionId to 2 because the website was completely redesigned
    // and some comic slugs have changed, requiring a full library migration.
    override val versionId = 2

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor { chain -> imageIntercept(chain) }
        .rateLimit(3)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36")
        .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
        .set("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
        .set("Sec-Ch-Ua", "\"Chromium\";v=\"133\", \"Google Chrome\";v=\"133\", \"Not-A.Brand\";v=\"99\"")
        .set("Sec-Ch-Ua-Mobile", "?0")
        .set("Sec-Ch-Ua-Platform", "\"Windows\"")
        .set("Sec-Fetch-Dest", "document")
        .set("Sec-Fetch-Mode", "navigate")
        .set("Sec-Fetch-Site", "none")
        .set("Sec-Fetch-User", "?1")
        .set("Upgrade-Insecure-Requests", "1")

    // ============================== Popular ==============================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/browse?sort=popular&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("a[href*=/comic/]").filter { it.isVisible() }
            .distinctBy { it.attr("href") }
            .mapNotNull { element ->
                val titleText = element.selectFirst("h3")?.text() ?: return@mapNotNull null
                val mangaUrl = element.absUrl("href").takeIf { it.isNotEmpty() } ?: return@mapNotNull null

                SManga.create().apply {
                    setUrlWithoutDomain(mangaUrl)
                    title = titleText
                    thumbnail_url = element.selectFirst("img")?.absUrl("src")
                }
            }

        val hasNextPage = document.selectFirst("a:contains(Selanjutnya)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/browse?sort=latest&page=$page", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("browse")
            addQueryParameter("q", query)
            addQueryParameter("page", page.toString())

            val sortFilter = filters.firstInstanceOrNull<SortFilter>()
            addQueryParameter("sort", sortFilter?.toUriPart() ?: "latest")

            filters.firstInstanceOrNull<MediaFilter>()?.let { addQueryParameter("media", it.toUriPart()) }
            filters.firstInstanceOrNull<TypeFilter>()?.let { addQueryParameter("type", it.toUriPart()) }
            filters.firstInstanceOrNull<StatusFilter>()?.let { addQueryParameter("status", it.toUriPart()) }
            filters.firstInstanceOrNull<GenreFilter>()?.let { addQueryParameter("genre", it.toUriPart()) }
            filters.firstInstanceOrNull<TeamFilter>()?.let { addQueryParameter("team", it.toUriPart()) }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // ============================== Details ==============================
    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun mangaDetailsRequest(manga: SManga): Request {
        // Migration from old web urls to the new one
        if (manga.url.contains("/komik/")) {
            throw Exception("Migrate dari $name ke $name (ekstensi yang sama)")
        }

        return GET(baseUrl + manga.url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            // Prioritize text-3xl class for the real title to avoid honeypots
            title = document.select("h1.text-3xl, h1._tt, h1").firstOrNull { it.isVisible() }?.text()
                ?: document.selectFirst("h1")?.text()
                ?: "Judul tidak ditemukan"
            thumbnail_url = document.selectFirst("div.aspect-\\[3\\/4\\] img")?.absUrl("src")
            author = document.select("span:contains(Author:) > span, span:contains(Uploaded by:) a").firstOrNull { it.isVisible() }?.text()
            artist = document.select("span:contains(Artist:) > span").firstOrNull { it.isVisible() }?.text()
            description = document.select("#synopsis-wrapper > div, #synopsis-text, div#_ds, div#_sd")
                .filter { it.isVisible() }
                .filter { !it.text().contains("Loading", true) }
                .firstNotNullOfOrNull { it.text() }
            genre = document.select("a[href*=/browse?genre=]").filter { it.isVisible() }.joinToString(", ") { it.text() }

            status = document.selectFirst(".flex.items-start.gap-3 span.border")?.text()?.lowercase()?.let {
                when {
                    it.contains("ongoing") -> SManga.ONGOING
                    it.contains("completed") -> SManga.COMPLETED
                    it.contains("hiatus") -> SManga.ON_HIATUS
                    else -> SManga.UNKNOWN
                }
            } ?: SManga.UNKNOWN
        }
    }

    // ============================= Chapters ==============================
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("a[href*=/read/]").filter { it.isVisible() }.mapNotNull { element ->
            val divs = element.select("> div")
            val nameText = divs.firstOrNull()?.text()?.trim() ?: return@mapNotNull null
            if (nameText.contains("Trap", true) || nameText.lowercase() == "chapter") return@mapNotNull null

            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = nameText
                date_upload = parseDate(divs.lastOrNull()?.select("span")?.lastOrNull()?.text())
            }
        }
    }

    // =============================== Pages ===============================
    override fun pageListRequest(chapter: SChapter): Request {
        // Migration from old web urls to the new one
        if (chapter.url.contains("/komik/")) {
            throw Exception("Migrate dari $name ke $name (ekstensi yang sama)")
        }

        return GET(baseUrl + chapter.url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val referer = response.request.url.toString()

        val pageElements = document.select("#reader-pages img[src], #reader-pages img[data-src], #reader-pages [data-sec-src]")

        if (pageElements.isEmpty()) {
            val title = document.title()
            if (title.contains("Just a moment", true) || title.contains("Cloudflare", true)) {
                throw Exception("Tolong selesaikan Captcha di WebView")
            }

            throw Exception("Tidak ada halaman ditemukan. Kemungkinan chapter kosong.")
        }

        return pageElements.mapNotNull { element ->
            val secSrc = element.attr("data-sec-src").trim()
            val xorKey = element.attr("data-xor-key").trim()

            if (secSrc.isNotEmpty() && xorKey.isNotEmpty()) {
                element.absUrl("data-sec-src") + "#" + xorKey
            } else {
                val src = element.attr("abs:data-src").takeIf { it.isNotEmpty() } ?: element.absUrl("src")
                src.takeIf { it.isNotEmpty() && !it.contains("avatar") && !it.contains("cover") }
            }
        }.distinct().mapIndexed { index, url ->
            Page(index, url = referer, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // =============================== Image ===============================
    override fun imageRequest(page: Page): Request {
        val isEncrypted = page.imageUrl?.contains("#") ?: false

        val newHeaders = headers.newBuilder().apply {
            set("Referer", page.url.takeIf { it.isNotEmpty() } ?: "$baseUrl/")

            if (isEncrypted) {
                set("Accept", "*/*")
                set("Sec-Fetch-Dest", "empty")
                set("Sec-Fetch-Mode", "cors")
            } else {
                set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                set("Sec-Fetch-Dest", "image")
                set("Sec-Fetch-Mode", "no-cors")
            }
            set("Sec-Fetch-Site", "same-origin")
            removeAll("Sec-Fetch-User")
            removeAll("Upgrade-Insecure-Requests")
        }.build()

        return GET(page.imageUrl ?: "", newHeaders)
    }

    // ============================== Filters ==============================
    override fun getFilterList() = FilterList(
        SortFilter(),
        MediaFilter(),
        TypeFilter(),
        StatusFilter(),
        GenreFilter(),
        TeamFilter(),
    )

    // ============================= Utilities =============================

    private fun Element.isVisible(): Boolean {
        var curr: Element? = this
        while (curr != null) {
            if (curr.isHidden()) return false
            curr = curr.parent()
        }
        return true
    }

    private fun Element.isHidden(): Boolean {
        val style = attr("style").lowercase().replace(" ", "")
        return attr("aria-hidden") == "true" ||
            style.contains("display:none") ||
            style.contains("clip:rect") ||
            style.contains("clip-path:inset") ||
            style.contains("left:-999") ||
            style.contains("opacity:0") ||
            style.contains("visibility:hidden") ||
            (style.contains("position:absolute") && (style.contains("width:1px") || style.contains("width:0px"))) ||
            className().contains("sr-only", true)
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr == null) return 0L
        val trimmed = dateStr.trim().lowercase()
        val value = trimmed.split(" ").firstOrNull()?.toIntOrNull() ?: return 0L

        return Calendar.getInstance().apply {
            when {
                trimmed.contains("detik") -> add(Calendar.SECOND, -value)
                trimmed.contains("menit") -> add(Calendar.MINUTE, -value)
                trimmed.contains("jam") -> add(Calendar.HOUR_OF_DAY, -value)
                trimmed.contains("hari") -> add(Calendar.DATE, -value)
                trimmed.contains("minggu") -> add(Calendar.DATE, -value * 7)
                trimmed.contains("bulan") -> add(Calendar.MONTH, -value)
                trimmed.contains("tahun") -> add(Calendar.YEAR, -value)
                else -> return 0L
            }
        }.timeInMillis
    }

    private fun imageIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val xorKey = url.fragment

        val response = chain.proceed(request)
        val body = response.body
        if (xorKey.isNullOrEmpty() || !response.isSuccessful) return response

        val decodedKey = try {
            val b64 = xorKey.replace("-", "+").replace("_", "/").let {
                it.padEnd(it.length + (4 - it.length % 4) % 4, '=')
            }
            Base64.decode(b64, Base64.DEFAULT).takeIf { it.isNotEmpty() } ?: xorKey.toByteArray()
        } catch (_: Exception) {
            xorKey.toByteArray()
        }

        val contentType = body.contentType()
        val keyLen = decodedKey.size

        if (keyLen == 0) return response

        val shiftArray = ByteArray(keyLen) { i ->
            (decodedKey[(i + 3) % keyLen].toInt() and 7).toByte()
        }

        val xorSource = object : ForwardingSource(body.source()) {
            var keyPos = 0

            override fun read(sink: Buffer, byteCount: Long): Long {
                val bytesRead = super.read(sink, byteCount)
                if (bytesRead == -1L) return -1L

                sink.readAndWriteUnsafe().use { cursor ->
                    cursor.seek(sink.size - bytesRead)

                    while (cursor.next().toLong() != -1L) {
                        val data = cursor.data!!
                        val start = cursor.start
                        val end = cursor.end

                        for (i in start until end) {
                            val k = keyPos % keyLen
                            val w = shiftArray[k].toInt()
                            val b = data[i].toInt() and 0xFF

                            val rotated = ((b ushr w) or (b shl (8 - w))) and 0xFF
                            data[i] = (rotated xor (decodedKey[k].toInt() and 0xFF)).toByte()
                            keyPos++
                        }
                    }
                }
                return bytesRead
            }
        }

        return response.newBuilder()
            .body(xorSource.buffer().asResponseBody(contentType))
            .build()
    }
}
