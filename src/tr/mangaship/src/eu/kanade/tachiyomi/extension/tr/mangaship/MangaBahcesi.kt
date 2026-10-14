package eu.kanade.tachiyomi.extension.tr.mangaship

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import java.io.IOException
import java.net.URLEncoder
import java.util.Calendar

@Source
abstract class MangaBahcesi : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(::decryptInterceptor)

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

    override suspend fun getPopularManga(page: Int) = parseMangas(
        client.get(
            "$baseUrl/Tr/PopulerMangalar?page=$page",
        ),
    )

    override suspend fun getLatestUpdates(page: Int) = parseMangas(
        client.get(
            "$baseUrl/Tr/YeniMangalar?page=$page",
        ),
    )

    private fun parseMangas(response: Response): MangasPage {
        val doc = response.asJsoup()
        val mangas = doc.select("div.zaman.boyut").map { element ->
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

        val hasNextPage = doc.selectFirst("ul.pagination li.active + li > a") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.count(String::isNotBlank) < 3) return null
        return fetchMangaUpdate(
            SManga.create().apply { this.url = url.encodedPath },
            emptyList(),
            true,
            false,
        ).manga
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
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

        return parseMangas(client.get(url))
    }

    override val supportRelatedMangasBySearch = true

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val doc = client.get(getMangaUrl(manga)).asJsoup()

        return SMangaUpdate(parseDetails(doc), parseChapters(doc))
    }

    private fun parseDetails(document: Document) = SManga.create().apply {
        title = document.selectFirst("div.details-title h2")?.text()!!
        setUrlWithoutDomain(document.location())
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

    private fun parseChapters(document: Document): List<SChapter> = document.select("div.plylist-single").mapNotNull { element ->
        SChapter.create().apply {
            val a = element.selectFirst(".plylist-single-content > a")
            val href = a?.attr("onclick")?.substringAfter("location.href='")?.substringBefore("'")
                ?: a?.attr("href") ?: return@mapNotNull null
            setUrlWithoutDomain(href)

            val chapterName = a?.text()?.trim() ?: "Bölüm"
            val isVip = element.select("span:contains(VIP)").isNotEmpty()
            name = if (isVip) "[VIP] $chapterName" else chapterName

            date_upload = element.selectFirst("li.movie-time a")?.text()?.let { parseDate(it) } ?: 0L
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        // Enforce the site's mandatory login rule to view page images.
        val cookies = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
        if (cookies.none { it.name == ".ASPXAUTH" }) {
            throw Exception("Bölümleri okumak için WebView üzerinden giriş yapmalısınız.")
        }

        val document = client.get(getChapterUrl(chapter)).asJsoup()
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

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData() = client.get("$baseUrl/Tr/Mangalar")
        .asJsoup()
        .select("select[name=kategori] option")
        .associate { it.text() to it.attr("value") }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        listOf<Filter<*>>(
            TypeFilter(),
            StatusFilter(),
        ) + buildList {
            val genres = data?.parseAs<Map<String, String>>().orEmpty()
            if (genres.isNotEmpty()) {
                add(
                    GenreFilter(genres.map { Genre(it.key, it.value) }),
                )
            }
        },
    )
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
