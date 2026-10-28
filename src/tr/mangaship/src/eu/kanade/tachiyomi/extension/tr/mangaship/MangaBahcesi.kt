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
import keiyoushi.utils.attrOrNull
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
        val mangas = doc.select("article.series-card").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a.series-card__link")!!.attr("abs:href"))
                title = element.selectFirst(".series-card__title")!!.text()

                val id = element.selectFirst("img.series-card__image")?.attrOrNull("data-cover-id")
                if (id != null) {
                    thumbnail_url = "$baseUrl/decrypt?id=${URLEncoder.encode(id, "UTF-8")}"
                }
            }
        }

        // the "next" arrow stays on the last page, just with a javascript:void(0) href
        val hasNextPage = doc.selectFirst("ul.pagination a.lastpage")
            ?.attr("href")?.startsWith("javascript") == false
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

        val response = client.get(url)
        // a single exact match redirects straight to the manga page
        if (response.request.url.pathSegments.getOrNull(1) == "Manga") {
            return MangasPage(listOf(parseDetails(response.asJsoup())), false)
        }

        return parseMangas(response)
    }

    override val supportRelatedMangasBySearch = true

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val doc = client.get(getMangaUrl(manga)).asJsoup()
        val details = parseDetails(doc)

        return SMangaUpdate(details, parseChapters(doc, details.title))
    }

    private fun parseDetails(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.manga-detail__title")!!.text()
        setUrlWithoutDomain(document.location())
        description = document.select("div.details-dectiontion p").text()

        val script = document.selectFirst("div#mangaKapakRes script")?.data()
        val id = script?.substringAfter("\"id\": '")?.substringBefore("'")
        if (!id.isNullOrEmpty()) {
            thumbnail_url = "$baseUrl/decrypt?id=${URLEncoder.encode(id, "UTF-8")}"
        }

        author = document.selectFirst("dl.manga-detail__facts dt:contains(Yazar) + dd")?.text()

        val badges = document.select("div.manga-detail__badges span").text().lowercase()
        status = when {
            badges.contains("devam ediyor") -> SManga.ONGOING
            badges.contains("tamamlandı") -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        val genres = document.select("div.manga-detail__genres a").map { it.text() }
        if (genres.isNotEmpty()) {
            genre = genres.joinToString()
        }
    }

    private fun parseChapters(document: Document, mangaTitle: String): List<SChapter> = document.select("div.plylist-single").mapNotNull { element ->
        SChapter.create().apply {
            val a = element.selectFirst(".plylist-single-content > a") ?: return@mapNotNull null
            setUrlWithoutDomain(a.attr("abs:href"))

            val chapterName = a.text().removePrefix(mangaTitle).trim()
            val isVip = element.select("span:contains(VIP)").isNotEmpty()
            name = if (isVip) "[VIP] $chapterName" else chapterName

            date_upload = element.selectFirst(".manga-detail__chapter-date")?.text()?.let { parseDate(it) } ?: 0L
        }
    }

    // VIP chapters come back without any page scripts unless logged in as a VIP member
    override suspend fun getPageList(chapter: SChapter): List<Page> {
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
            // site assets like the end-of-chapter banner (son2.jpg) and the readingcontent.jpg placeholder
            if (!src.contains("/Content/img/", true)) {
                pages.add(Page(i++, imageUrl = src))
            }
        }

        return pages
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData() = client.get("$baseUrl/Tr/Mangalar")
        .asJsoup()
        .select("label.catalog-search__option:has(input[name=kategori])")
        .associate { it.text() to it.selectFirst("input")!!.attr("value") }
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
