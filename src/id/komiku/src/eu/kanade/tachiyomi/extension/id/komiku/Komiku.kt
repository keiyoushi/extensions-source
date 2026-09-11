package eu.kanade.tachiyomi.extension.id.komiku

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

@Source
abstract class Komiku : KeiSource() {

    private val apiUrl = "https://api.komiku.org"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::headersInterceptor)
        .rateLimit(2)

    override fun Headers.Builder.configureHeaders(): Headers.Builder = set("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = if (page > 1) {
            "$apiUrl/other/hot/page/$page/".toHttpUrl()
        } else {
            "$apiUrl/other/hot/".toHttpUrl()
        }.newBuilder()
            .addQueryParameter("orderby", "meta_value_num")
            .build()

        return mangaListParse(client.get(url).asJsoup())
    }

    // =============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = mangaApiUrlBuilder(page).addQueryParameter("orderby", "modified").build()
        return mangaListParse(client.get(url).asJsoup())
    }

    // =============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = mangaApiUrlBuilder(page).apply {
            if (query.isNotEmpty()) {
                addQueryParameter("s", query)
            }

            filters.filterIsInstance<UriFilter>().forEach {
                it.addToUri(this)
            }
        }.build()

        return mangaListParse(client.get(url).asJsoup())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() != "manga") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val targetUrl = "$baseUrl/manga/$slug/".toHttpUrl()
        val document = client.get(targetUrl).asJsoup()
        val manga = SManga.create().apply {
            setUrlWithoutDomain(targetUrl.toString())
        }
        return parseDetails(document, manga)
    }

    private fun mangaApiUrlBuilder(page: Int) = apiUrl.toHttpUrl().newBuilder().apply {
        addPathSegment("manga")
        if (page > 1) {
            addPathSegments("page/$page")
        }
    }

    // ======================= Details and Chapters ==========================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()
        return SMangaUpdate(
            manga = parseDetails(document, manga),
            chapters = parseChapters(document),
        )
    }

    private fun parseDetails(document: Document, manga: SManga): SManga = manga.apply {
        description = buildString {
            append(document.select("#Sinopsis > p, p.desc[itemprop=description]").text())

            document.selectFirst("table.inftable tr:contains(Judul Indonesia) td + td, table.inftable tr:contains(Judul Alternatif) td + td")?.text()?.let {
                if (it.isNotEmpty()) {
                    if (isNotEmpty()) append("\n\n")
                    append("Judul Alternatif: $it")
                }
            }
        }

        author = document.selectFirst("table.inftable td:contains(Pengarang)+td, table.inftable td:contains(Komikus)+td, table.inftable td:contains(Author)+td")?.text()
        genre = document.select("ul.genre li.genre a span").joinToString { it.text() }.takeIf { it.isNotEmpty() }
        status = parseStatus(document.selectFirst("table.inftable tr > td:contains(Status) + td")?.text())
        thumbnail_url = document.selectFirst("div.ims > img, img[itemprop=image]")?.absUrl("src")?.removeQuery()
    }

    private fun parseStatus(status: String?): Int {
        val s = status?.lowercase() ?: return SManga.UNKNOWN
        return when {
            "ongoing" in s || "on going" in s -> SManga.ONGOING
            "end" in s || "completed" in s || "tamat" in s -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private fun parseChapters(document: Document): List<SChapter> = document.select("#Daftar_Chapter tr:has(td.judulseries)").map { element ->
        SChapter.create().apply {
            val a = element.selectFirst("a")!!
            setUrlWithoutDomain(a.absUrl("href"))
            name = a.text()

            val timeStamp = element.selectFirst("td.tanggalseries")?.text().orEmpty()
            date_upload = if (timeStamp.contains("lalu")) {
                parseRelativeDate(timeStamp)
            } else {
                dateFormat.tryParseDate(timeStamp, ZoneId.of("Asia/Jakarta"))
            }
        }
    }

    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    private fun parseRelativeDate(date: String): Long {
        val trimmedDate = date.substringBefore(" lalu").trim().split(" ")
        if (trimmedDate.size < 2) return 0L
        val amount = trimmedDate[0].toIntOrNull() ?: return 0L

        val calendar = Calendar.getInstance()
        when (trimmedDate[1]) {
            "detik" -> calendar.add(Calendar.SECOND, -amount)
            "menit" -> calendar.add(Calendar.MINUTE, -amount)
            "jam" -> calendar.add(Calendar.HOUR_OF_DAY, -amount)
            "hari" -> calendar.add(Calendar.DAY_OF_YEAR, -amount)
            "minggu" -> calendar.add(Calendar.WEEK_OF_YEAR, -amount)
            "bulan" -> calendar.add(Calendar.MONTH, -amount)
            "tahun" -> calendar.add(Calendar.YEAR, -amount)
        }

        return calendar.timeInMillis
    }

    // =============================== Pages ================================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = baseUrl + chapter.url
        val document = client.get(chapterUrl).asJsoup()
        return document.select("#Baca_Komik img")
            .filterNot { it.attr("src").contains("komiku-promosi") }
            .mapIndexed { i, element ->
                Page(i, chapterUrl, imageUrl = element.attr("abs:src"))
            }
    }

    override fun imageRequest(page: Page): Request {
        val headers = headersBuilder()
            .set("Referer", page.url)
            .build()
        return GET(page.imageUrl!!, headers)
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Type(),
        Order(),
        Genre1(),
        Genre2(),
        Status(),
    )

    // ============================= Utilities ==============================
    private fun headersInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val urlString = url.toString()

        if (urlString.contains("komiku.org") || urlString.contains("komikid.org") || urlString.contains("komiku.to")) {
            val newHeaders = request.headers.newBuilder().apply {
                removeAll("X-Requested-With")
                set("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")

                if (url.host.contains("img") || url.host.contains("thumbnail") || url.host.contains("update") || url.host.contains("image")) {
                    val referer = request.header("Referer")
                    if (referer == null || !referer.contains(baseUrl)) {
                        set("Referer", "$baseUrl/")
                    }
                    set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                    set("Sec-Fetch-Dest", "image")
                    set("Sec-Fetch-Mode", "no-cors")
                    set("Sec-Fetch-Site", if (urlString.contains("komiku.org")) "same-site" else "cross-site")
                }
            }.build()

            return chain.proceed(
                request.newBuilder()
                    .headers(newHeaders)
                    .build(),
            )
        }

        return chain.proceed(request)
    }

    private fun mangaListParse(document: Document): MangasPage {
        val mangas = document.select("div.bge").map { element ->
            SManga.create().apply {
                title = element.selectFirst("h3")!!.text()
                setUrlWithoutDomain(element.selectFirst("a:has(h3)")!!.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.absUrl("src")?.removeQuery()
            }
        }
        val hasNextPage = document.selectFirst("span[hx-get]") != null || mangas.size >= 10
        return MangasPage(mangas, hasNextPage)
    }

    private fun String.removeQuery() = toHttpUrlOrNull()?.newBuilder()?.query(null)?.build()?.toString() ?: this
}
