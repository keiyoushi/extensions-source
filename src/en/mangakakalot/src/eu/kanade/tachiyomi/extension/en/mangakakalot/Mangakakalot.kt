package eu.kanade.tachiyomi.extension.en.mangakakalot

import eu.kanade.tachiyomi.multisrc.mangabox.MangaBox
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Dispatcher
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
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

    private val idSlugRegex = Regex("^[a-z]{2}\\d+$")

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
        .build()

    /* ================================
     * Manga Details
     * ================================ */

    override fun mangaDetailsRequest(manga: SManga): Request {
        val currentSlug = manga.url.substringAfterLast("/")
        val targetUrl = if (currentSlug.matches(idSlugRegex)) {
            val newSlug = titleToSlug(manga.title)
            "$baseUrl/manga/$newSlug"
        } else {
            "$baseUrl${manga.url}"
        }

        return GET(targetUrl, headers)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = super.mangaDetailsParse(document)
        val pageUrl = document.location().toHttpUrlOrNull()
        val actualSlug = pageUrl
            ?.pathSegments
            ?.dropWhile { it != "manga" }
            ?.getOrNull(1)
            ?: titleToSlug(manga.title)
        manga.url = "/manga/$actualSlug"
        return manga
    }

    /* ================================
     * Chapter List
     * ================================ */

    override fun chapterListRequest(manga: SManga): Request {
        val currentSlug = manga.url.substringAfterLast("/")
        val isIdSlug = currentSlug.matches(idSlugRegex)
        val apiSlug = if (isIdSlug) {
            val cleanSlug = titleToSlug(manga.title)
            manga.url = "/manga/$cleanSlug"
            cleanSlug
        } else {
            currentSlug
        }

        val apiUrl = "$baseUrl/api/manga/$apiSlug/chapters?limit=-1"
        return GET(apiUrl, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val slug = response.request.url.pathSegments
            .dropWhile { it != "manga" }
            .getOrNull(1)
            ?: return emptyList()

        val result = runCatching {
            response.parseAs<ChapterResponseDto>()
        }.getOrElse { return emptyList() }

        if (!result.success) return emptyList()

        return result.data?.chapters.orEmpty().mapNotNull { item ->
            val chapterSlug = item.slug ?: return@mapNotNull null

            SChapter.create().apply {
                name = item.name ?: "Chapter"
                chapter_number = item.num ?: 0f
                url = "$baseUrl/manga/$slug/$chapterSlug"
                date_upload = item.updatedAt
                    ?.let { jsonDateFormat.tryParse(it) }
                    ?: 0L
            }
        }
    }
}
