package eu.kanade.tachiyomi.extension.en.nuxscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class NuxScans : HttpSource() {

    override val name = "Nux Scans"

    override val baseUrl = "https://nuxscans-comics.blogspot.com"

    override val lang = "en"

    override val supportsLatest = true

    companion object {
        private val JS_REDIRECT_REGEX = Regex("""window\.location\.replace\(['"]([^'"]+)['"]\)""")
    }

    // Blogger redirects mobile User-Agents to ?m=1, which returns 404 for chapter posts.
    // Must be a NETWORK interceptor so it runs for every individual HTTP call, including
    // the ones OkHttp makes internally when following 302 redirects. An application
    // interceptor only sees the original request and would miss the ?m=1 redirect target.
    private val bloggerMobileInterceptor = Interceptor { chain ->
        val original = chain.request()
        val url = original.url

        val fixedUrl = if (url.queryParameter("m") == "1") {
            url.newBuilder().setQueryParameter("m", "0").build()
        } else {
            url
        }

        chain.proceed(original.newBuilder().url(fixedUrl).build())
    }

    private val jsRedirectInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.isSuccessful && response.header("Content-Type")?.contains("text/html") == true) {
            val bodyString = response.peekBody(1024 * 1024).string()
            val match = JS_REDIRECT_REGEX.find(bodyString)
            if (match != null) {
                var newUrl = match.groupValues[1]
                if (!newUrl.startsWith("http")) {
                    newUrl = baseUrl + if (newUrl.startsWith("/")) newUrl else "/$newUrl"
                }
                response.close()
                return@Interceptor chain.proceed(request.newBuilder().url(newUrl).build())
            }
        }
        response
    }

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addNetworkInterceptor(bloggerMobileInterceptor) // network interceptor: runs for redirect targets too
        .addInterceptor(jsRedirectInterceptor)
        .build()

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(".index-post").map { element ->
            SManga.create().apply {
                val a = element.selectFirst(".post-title a")!!
                title = a.text()
                setUrlWithoutDomain(a.attr("href"))

                val img = element.selectFirst(".post-thumb")
                thumbnail_url = img?.attr("abs:data-src")?.ifEmpty { img.attr("abs:src") }
            }
        }

        return MangasPage(mangas, false)
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/search?q=$query", headers)

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // =========================== Manga Details ============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1.post-title")?.text() ?: ""
            description = document.selectFirst(".post-details h3:contains(Synopsis) + p")?.text()
            thumbnail_url = document.selectFirst(".post-thumbnail img")?.attr("abs:src")

            author = document.selectFirst(".post-details p:contains(Author:)")
                ?.text()
                ?.substringAfter("Author:")

            status = document.selectFirst(".post-details p:contains(Status:)")?.text()?.let {
                when {
                    it.contains("Ongoing", ignoreCase = true) -> SManga.ONGOING
                    it.contains("Completed", ignoreCase = true) -> SManga.COMPLETED
                    it.contains("Dropped", ignoreCase = true) -> SManga.CANCELLED
                    else -> SManga.UNKNOWN
                }
            } ?: SManga.UNKNOWN

            genre = document.select(".post-tab-genre .post-genre a").joinToString(", ") { it.text() }
        }
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select(".row-chapters .list-item a").map { a ->
            SChapter.create().apply {
                // Store the full absolute URL because chapters live on nuxscans.blogspot.com,
                // not on baseUrl (nuxscans-comics.blogspot.com). pageListRequest below uses
                // it directly instead of prepending baseUrl.
                url = a.attr("abs:href")
                val nameText = a.text()
                name = if (nameText.toDoubleOrNull() != null) "Chapter $nameText" else nameText
            }
        }.reversed()
    }

    // =============================== Pages ================================

    // chapter.url is a full absolute URL (may be on nuxscans.blogspot.com, not baseUrl),
    // so we must NOT prepend baseUrl here — use it as-is.
    override fun pageListRequest(chapter: SChapter): Request = GET(chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select(".post-body img, .holder img").filterNot { img ->
            val src = img.attr("abs:src").lowercase()
            src.contains("logo") || src.contains("footer") || src.contains("credit") || img.hasClass("watermark")
        }.mapIndexed { i, img ->
            Page(i, imageUrl = img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
