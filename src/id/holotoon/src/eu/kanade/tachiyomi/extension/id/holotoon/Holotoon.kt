package eu.kanade.tachiyomi.extension.id.holotoon

import android.util.Base64
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.randomua.addRandomUAPreference
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer

class Holotoon :
    HttpSource(),
    ConfigurableSource {

    override val name = "Holotoon"

    override val baseUrl = "https://v1.holotoon.site"

    override val lang = "id"

    override val supportsLatest = true

    override val versionId = 2

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::imageIntercept)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .setRandomUserAgent()
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
        .add("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
        .add("Upgrade-Insecure-Requests", "1")
        .add("Sec-Fetch-Dest", "document")
        .add("Sec-Fetch-Mode", "navigate")
        .add("Sec-Fetch-Site", "same-origin")
        .add("Sec-Fetch-User", "?1")

    // ============================== Popular ==============================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/browse?sort=popular&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("a.comic-card-link")
            .distinctBy { it.attr("href") }
            .mapNotNull { element ->
                val titleText = element.selectFirst("h3")?.text()
                if (titleText.isNullOrEmpty()) return@mapNotNull null

                SManga.create().apply {
                    setUrlWithoutDomain(element.absUrl("href"))
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

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.text-3xl.font-bold")!!.text()
            thumbnail_url = document.selectFirst("div.aspect-\\[3\\/4\\] img")?.absUrl("src")
            author = document.selectFirst("span:contains(Uploaded by:) a")?.text()
            description = document.selectFirst("#synopsis-text")?.text()
            genre = document.select("a[href*=/browse?genre=]").joinToString(", ") { it.text() }

            val statusText = document.selectFirst(".flex.items-start.gap-3 span.border")?.text()?.lowercase() ?: ""
            status = when {
                statusText.contains("ongoing") -> SManga.ONGOING
                statusText.contains("completed") -> SManga.COMPLETED
                statusText.contains("hiatus") -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        }
    }

    // ============================= Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("#chapter-list-scroll a.chapter-row").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.select(".chapter-link").joinToString(" ") { it.text() }
                date_upload = parseDate(element.selectFirst("span.text-right")?.text() ?: "")
            }
        }
    }

    // =============================== Pages ===============================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select("#reader-pages > div[data-page-idx]").mapIndexed { index, div ->
            val secSrc = div.attr("data-sec-src")
            val xorKey = div.attr("data-xor-key")

            val url = if (secSrc.isNotEmpty() && xorKey.isNotEmpty()) {
                div.absUrl("data-sec-src") + "#" + xorKey
            } else {
                div.selectFirst("img")?.absUrl("src") ?: ""
            }

            Page(index, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

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
    private fun parseDate(dateStr: String): Long {
        val trimmed = dateStr.trim().lowercase()
        return when {
            trimmed.contains("baru saja") -> System.currentTimeMillis()
            trimmed.contains("detik") -> {
                val seconds = trimmed.substringBefore(" ").toLongOrNull() ?: 0
                System.currentTimeMillis() - seconds * 1000L
            }
            trimmed.contains("menit") -> {
                val minutes = trimmed.substringBefore(" ").toLongOrNull() ?: 0
                System.currentTimeMillis() - minutes * 60000L
            }
            trimmed.contains("jam") -> {
                val hours = trimmed.substringBefore(" ").toLongOrNull() ?: 0
                System.currentTimeMillis() - hours * 3600000L
            }
            trimmed.contains("hari") -> {
                val days = trimmed.substringBefore(" ").toLongOrNull() ?: 0
                System.currentTimeMillis() - days * 86400000L
            }
            trimmed.contains("minggu") -> {
                val weeks = trimmed.substringBefore(" ").toLongOrNull() ?: 0
                System.currentTimeMillis() - weeks * 7 * 86400000L
            }
            trimmed.contains("bulan") -> {
                val months = trimmed.substringBefore(" ").toLongOrNull() ?: 0
                System.currentTimeMillis() - months * 30 * 86400000L
            }
            trimmed.contains("tahun") -> {
                val years = trimmed.substringBefore(" ").toLongOrNull() ?: 0
                System.currentTimeMillis() - years * 365 * 86400000L
            }
            else -> 0L
        }
    }

    private fun imageIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val xorKey = url.fragment

        val response = chain.proceed(request)
        if (xorKey.isNullOrEmpty()) return response

        val decodedKey = try {
            Base64.decode(xorKey, Base64.URL_SAFE).takeIf { it.isNotEmpty() } ?: xorKey.toByteArray()
        } catch (_: Exception) {
            xorKey.toByteArray()
        }

        val body = response.body
        val contentType = body.contentType()

        // Decrypt the image using streaming avoiding out-of-memory errors
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
                            data[i] = (data[i].toInt() xor decodedKey[keyPos % decodedKey.size].toInt()).toByte()
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

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addRandomUAPreference()
    }
}
