package eu.kanade.tachiyomi.extension.en.mangakakalot

import eu.kanade.tachiyomi.multisrc.mangabox.MangaBox
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class Mangakakalot :
    MangaBox(
        "Mangakakalot",
        arrayOf(
            "www.mangakakalot.gg",
            "www.mangakakalove.com",
        ),
        "en",
    ) {

    /* ================================
     * Slug Utilities
     * ================================ */

    private fun titleToSlug(title: String): String = title
        .lowercase(Locale.ENGLISH)
        .replace("['']".toRegex(), "")
        .replace("[^a-z0-9\\s-]".toRegex(), "")
        .trim()
        .replace("[\\s-]+".toRegex(), "-")
        .trim('-')

    private val jsonDateFormat = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
        Locale.US,
    ).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Resolves download issues for very large chapters (~100+ pages).
     *
     * Mangakakalot slows down or rejects image-heavy requests when connections
     * are reused aggressively. Rate limiting alone (jitter) was insufficient.
     *
     * - Dispatcher limits parallel image requests
     * - "Connection: close" forces socket reset per image
     * - Prevents per-page stalls (30s to minutes) near chapter end
     */

    private val imageHeavyDispatcher = Dispatcher().apply {
        maxRequests = 8
        maxRequestsPerHost = 3
    }

    override val client: OkHttpClient = super.client.newBuilder()
        .dispatcher(imageHeavyDispatcher)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .addInterceptor(ApiHeadersInterceptor())
        .build()

    /* ================================
     * Manga Details
     * ================================ */

    override fun mangaDetailsRequest(manga: SManga): Request {
        val titleSlug = titleToSlug(manga.title)
        manga.url = "/manga/$titleSlug"
        return super.mangaDetailsRequest(manga)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = super.mangaDetailsParse(document)

        val pageUrl = document.location()
        val actualSlug = if (pageUrl.contains("/manga/")) {
            pageUrl.substringAfter("/manga/")
                .substringBefore("?")
                .substringBefore("#")
        } else {
            titleToSlug(manga.title)
        }

        manga.url = "/manga/$actualSlug"
        return manga
    }

    /* ================================
     * Chapter List
     * ================================ */

    override fun chapterListRequest(manga: SManga): Request {
        val currentSlug = manga.url.substringAfterLast("/")
        val isIdSlug = currentSlug.matches(Regex("^[a-z]{2}\\d+$"))

        val slug = if (manga.url.startsWith("/manga/") && !isIdSlug) {
            currentSlug
        } else {
            val titleSlug = titleToSlug(manga.title)
            manga.url = "/manga/$titleSlug"
            titleSlug
        }

        val apiUrl = "$baseUrl/api/manga/$slug/chapters?limit=-1"
        return GET(apiUrl, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        val body = response.body.string()
        if (body.isEmpty()) return chapters

        val json = JSONObject(body)
        if (!json.optBoolean("success")) return chapters

        val data = json.optJSONObject("data") ?: return chapters
        val jsonChapters = data.optJSONArray("chapters") ?: return chapters

        val slug = response.request.url.pathSegments
            .dropWhile { it != "manga" }
            .getOrNull(1)
            ?: return chapters

        for (i in 0 until jsonChapters.length()) {
            val item = jsonChapters.getJSONObject(i)
            val chapter = SChapter.create()

            chapter.name = item.optString("chapter_name", "Chapter")
            chapter.chapter_number = item.optDouble("chapter_num", 0.0).toFloat()
            chapter.url = "/manga/$slug/${item.optString("chapter_slug")}"

            item.optString("updated_at").takeIf { it.isNotEmpty() }?.let {
                chapter.date_upload =
                    runCatching { jsonDateFormat.parse(it)?.time }.getOrNull() ?: 0L
            }

            chapters.add(chapter)
        }

        return chapters
    }

    /* ================================
     * Page List
     * ================================ */

    override fun pageListParse(document: Document): List<Page> = document
        .select("div.container-chapter-reader img")
        .mapIndexedNotNull { i, img ->
            val url = img.attr("abs:src").ifEmpty { img.attr("abs:data-src") }
            if (url.isNotEmpty()) Page(i, "", url) else null
        }

    /* ================================
     * Image Requests
     * ================================ */

    override fun imageRequest(page: Page): Request = GET(
        page.imageUrl.orEmpty(),
        headersBuilder()
            .set("Referer", "$baseUrl/")
            .set("Connection", "close")
            .build(),
    )
}

    /* ================================
     * API 403 BYPASS
     * ================================ */

class ApiHeadersInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val url = req.url.toString()

        if (url.contains("/api/manga/")) {
            val segments = req.url.pathSegments
            val idx = segments.indexOf("manga")

            if (idx != -1 && idx + 1 < segments.size) {
                val slug = segments[idx + 1]
                val referer = "https://${req.url.host}/manga/$slug"

                val newReq = req.newBuilder()
                    .header("Referer", referer)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .build()

                return chain.proceed(newReq)
            }
        }

        return chain.proceed(req)
    }
}
