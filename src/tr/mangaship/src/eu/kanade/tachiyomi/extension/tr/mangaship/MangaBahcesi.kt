@file:Suppress("SpellCheckingInspection")

package eu.kanade.tachiyomi.extension.tr.mangaship

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.util.Calendar

class MangaBahcesi : HttpSource() {

    override val id: Long = 7110025728969951060
    override val name = "Manga Bahçesi"
    override val baseUrl = "https://mangabahcesi.com"

    override val lang = "tr"

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", baseUrl)

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::decryptInterceptor)
        .build()

    // Manga Bahçesi encrypts their image URLs (both covers and pages).
    // Instead of overriding 'imageUrlRequest' individually, we intercept generic requests internally,
    // hit their decryption API (/PartialView/SifreCoz), and seamlessly stream the decrypted image URLs to Coil/Mihon.
    private fun decryptInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.url.pathSegments.firstOrNull() == "decrypt") {
            val id = request.url.queryParameter("id") ?: return chain.proceed(request)

            val token = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
                .firstOrNull { it.name == "mangaToken" }?.value ?: ""

            val formBody = FormBody.Builder()
                .add("id", id)
                .add("token", token)
                .build()

            val postRequest = Request.Builder()
                .url("$baseUrl/PartialView/SifreCoz")
                .post(formBody)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "*/*")
                .header("Origin", baseUrl)
                .header("Referer", baseUrl)
                .build()

            val response = chain.proceed(postRequest)

            if (response.isSuccessful) {
                val path = response.body.string().trim('"')
                if (path.isNotBlank()) {
                    val realUrl = if (path.startsWith("http")) path else baseUrl + path
                    val realReq = request.newBuilder().url(realUrl).get().build()
                    return chain.proceed(realReq)
                }
            }

            response.close()
            throw IOException("Resim adresi çözülemedi. HTTP ${response.code}")
        }
        return chain.proceed(request)
    }

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/Tr/PopulerMangalar?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("div.zaman.boyut").map { element ->
            SManga.create().apply {
                val a = element.selectFirst("a")!!
                setUrlWithoutDomain(a.attr("abs:href"))
                title = a.attr("title").trim()

                val script = element.selectFirst("script:containsData(SifreCoz)")?.data()
                val id = script?.substringAfter("\"id\": '")?.substringBefore("'")
                if (!id.isNullOrEmpty()) {
                    thumbnail_url = "$baseUrl/decrypt?id=${URLEncoder.encode(id, "UTF-8")}"
                }
            }
        }

        val hasNextPage = document.selectFirst("ul.pagination li.active + li > a") != null

        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/Tr/YeniMangalar?page=$page", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun getFilterList() = FilterList(getFilters())

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("Tr")
            addPathSegment("Search")
            addQueryParameter("tur", "Manga")

            if (query.isNotBlank()) {
                addQueryParameter("kelime", query)
            }

            filters.firstInstanceOrNull<TypeFilter>()?.let {
                addQueryParameter("mangaTuru", it.toUriPart())
            }

            filters.firstInstanceOrNull<StatusFilter>()?.let {
                addQueryParameter("durum", it.toUriPart())
            }

            filters.firstInstanceOrNull<GenreFilter>()?.state
                ?.filter { it.state }
                ?.forEach {
                    addQueryParameter("kategori", it.id)
                }

            addQueryParameter("page", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("div.details-title h2")?.text() ?: ""
            description = document.select("div.details-dectiontion p").text()

            val script = document.selectFirst("div#mangaKapakRes script")?.data()
            val id = script?.substringAfter("\"id\": '")?.substringBefore("'")
            if (!id.isNullOrEmpty()) {
                thumbnail_url = "$baseUrl/decrypt?id=${URLEncoder.encode(id, "UTF-8")}"
            }

            val metadataElements = document.select("div.dec-review-meta ul li")
            for (element in metadataElements) {
                val label = element.selectFirst("span.left")?.text().orEmpty()
                val value = element.select("div.left a, a").text().trim()

                when {
                    label.contains("Yazar", true) -> author = value
                    label.contains("Durum", true) -> status = when {
                        value.contains("Devam Ediyor", true) -> SManga.ONGOING
                        value.contains("Tamamlandı", true) -> SManga.COMPLETED
                        else -> SManga.UNKNOWN
                    }
                }
            }

            val genres = document.select("div.dec-review-meta ul li:contains(Kategori) a").map { it.text().trim() }
            if (genres.isNotEmpty()) {
                genre = genres.joinToString(", ")
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select("div.plylist-single").map { element ->
            SChapter.create().apply {
                val a = element.selectFirst(".plylist-single-content > a")
                val href = a?.attr("onclick")?.substringAfter("location.href='")?.substringBefore("'")
                    ?: a?.attr("href") ?: ""
                setUrlWithoutDomain(href)

                val chapterName = a?.text()?.trim() ?: "Bölüm"
                val isVip = element.select("span:contains(VIP)").isNotEmpty()
                name = if (isVip) "[VIP] $chapterName" else chapterName

                date_upload = element.selectFirst("li.movie-time a")?.text()?.let { parseDate(it) } ?: 0L
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        // Enforce the site's mandatory login rule to view page images.
        val cookies = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
        if (cookies.none { it.name == ".ASPXAUTH" }) {
            throw Exception("Bölümleri okumak için WebView üzerinden giriş yapmalısınız.")
        }

        val document = response.asJsoup()
        val pages = mutableListOf<Page>()
        var i = 0

        document.select("div.reading-content script:containsData(SifreCoz), div.reading-content-manga script:containsData(SifreCoz)").forEach { script ->
            val id = script.data().substringAfter("\"id\": '").substringBefore("'")
            if (id.isNotBlank()) {
                pages.add(Page(i++, imageUrl = "$baseUrl/decrypt?id=${URLEncoder.encode(id, "UTF-8")}"))
            }
        }

        document.select("div.reading-content img#mangaOkuResim, div.reading-content-manga img#mangaOkuResim").forEach { img ->
            val src = img.attr("abs:src")
            // Ensure placeholder dummy images injected by the source aren't fetched
            if (!src.contains("readingcontent.jpg", true)) {
                pages.add(Page(i++, imageUrl = src))
            }
        }

        return pages
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun parseDate(dateStr: String): Long {
        val now = Calendar.getInstance()
        val num = dateStr.filter { it.isDigit() }.toIntOrNull() ?: 1

        when {
            dateStr.contains("dakika", true) -> now.add(Calendar.MINUTE, -num)
            dateStr.contains("saat", true) -> now.add(Calendar.HOUR_OF_DAY, -num)
            dateStr.contains("gün", true) -> now.add(Calendar.DAY_OF_YEAR, -num)
            dateStr.contains("hafta", true) -> now.add(Calendar.WEEK_OF_YEAR, -num)
            dateStr.contains("ay", true) -> now.add(Calendar.MONTH, -num)
            dateStr.contains("yıl", true) -> now.add(Calendar.YEAR, -num)
        }
        return now.timeInMillis
    }
}
