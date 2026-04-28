package eu.kanade.tachiyomi.extension.vi.yurigarden

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.lib.cryptoaes.CryptoAES
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import rx.Observable
import java.util.concurrent.TimeUnit

class YuriGarden :
    HttpSource(),
    ConfigurableSource {

    override val name = "YuriGarden"

    override val lang = "vi"

    override val baseUrl = "https://yurigarden.com"

    override val supportsLatest = true

    private val apiUrl = baseUrl.replace("://", "://api.") + "/api"

    private val dbUrl = baseUrl.replace("://", "://db.")

    private val preferences by getPreferencesLazy()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageDescrambler())
        .rateLimitHost(apiUrl.toHttpUrl(), 15, 1, TimeUnit.MINUTES)
        .build()

    private fun apiHeaders() = headersBuilder()
        .set("Referer", "$baseUrl/")
        .add("x-app-origin", baseUrl)
        .add("x-custom-lang", "vi")
        .add("Accept", "application/json")
        .build()

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/comics/rank/trending".toHttpUrl().newBuilder()
            .addQueryParameter("type", "day")
            .addQueryParameter("r18", allowR18.toString())
            .build()

        return GET(url, apiHeaders())
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<List<TrendingComic>>()

        val mangaList = result.map { comic ->
            SManga.create().apply {
                url = "/comic/${comic.id}"
                title = comic.title
                thumbnail_url = comic.image.takeIf(String::isNotBlank)?.toThumbnailUrl()
            }
        }

        val hasNextPage = false // The trending endpoint does not support pagination

        return MangasPage(mangaList, hasNextPage)
    }

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/comics".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", LIMIT.toString())
            .addQueryParameter("r18", allowR18.toString())
            .addQueryParameter("full", "true")
            .build()

        return GET(url, apiHeaders())
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<ComicsResponse>()

        val mangaList = result.comics.map { comic ->
            SManga.create().apply {
                url = "/comic/${comic.id}"
                title = comic.title
                thumbnail_url = comic.thumbnail?.toThumbnailUrl()
            }
        }

        val hasNextPage = result.totalPages > currentPage(response)

        return MangasPage(mangaList, hasNextPage)
    }

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/comics".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", LIMIT.toString())
            addQueryParameter("allowR18", allowR18.toString())
            addQueryParameter("full", "true")

            setQueryParameter("searchBy", "title,anotherNames")

            if (query.isNotBlank()) {
                addQueryParameter("search", query)
            }

            val filterList = filters.ifEmpty { getFilterList() }

            filterList.forEach { filter ->
                when (filter) {
                    is StatusFilter -> {
                        if (filter.slug.isNotEmpty()) {
                            addQueryParameter("status", filter.slug)
                        }
                    }
                    is SortFilter -> {
                        addQueryParameter("sort", filter.slug)
                    }
                    is GenreFilter -> {
                        val selected = filter.state
                            .filter { it.state }
                            .joinToString(",") { it.value }
                        if (selected.isNotEmpty()) {
                            addQueryParameter("genre", selected)
                        }
                    }
                    is SearchByFilter -> {
                        val selected = filter.state
                            .filter { it.state }
                            .joinToString(",") { it.value }
                        if (selected.isNotEmpty()) {
                            setQueryParameter("searchBy", selected)
                        }
                    }
                    else -> {}
                }
            }
        }.build()

        return GET(url.toString(), apiHeaders())
    }

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    // ============================== Filters ===============================

    override fun getFilterList() = getFilters()

    // ============================== Details ===============================

    private fun mangaId(manga: SManga): String = manga.url.substringAfterLast("/")

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/comics/${mangaId(manga)}", apiHeaders())

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga {
        val comic = response.parseAs<ComicDetail>()

        return SManga.create().apply {
            url = "/comic/${comic.id}"
            title = comic.title
            author = comic.authors.joinToString { it.name }
            description = comic.description
            genre = comic.genres.mapNotNull { genreMap[it] }.joinToString()
            status = when (comic.status) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "hiatus" -> SManga.ON_HIATUS
                "canceled" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
            thumbnail_url = comic.thumbnail?.toThumbnailUrl()
            initialized = true
        }
    }

    // ============================== Chapters ==============================

    private fun chapterId(chapter: SChapter): String = chapter.url.substringAfterLast("/")

    override fun chapterListRequest(manga: SManga): Request = GET("$apiUrl/chapters/comic/${mangaId(manga)}", apiHeaders())

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = response.parseAs<List<ChapterData>>()

        val comicId = response.request.url.pathSegments.last()

        return chapters
            .sortedWith(
                compareByDescending<ChapterData> { it.order }
                    .thenByDescending { it.id },
            )
            .map { chapter ->
                SChapter.create().apply {
                    url = "/comic/$comicId/${chapter.id}"
                    name = buildString {
                        if (chapter.volume != null) {
                            append("Vol.${chapter.volume.toBigDecimal().stripTrailingZeros().toPlainString()} ")
                        }
                        if (chapter.order < 0) {
                            append("Oneshot")
                        } else {
                            append("Ch.${chapter.order.toBigDecimal().stripTrailingZeros().toPlainString()}")
                        }
                        if (chapter.name.isNotEmpty()) append(": ${chapter.name}")
                    }
                    date_upload = chapter.publishedAt
                    chapter_number = chapter.order.toFloat()
                    scanlator = chapter.team?.name ?: "Unknown"
                }
            }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // ============================== Pages =================================

    override fun pageListRequest(chapter: SChapter): Request = GET("$apiUrl/chapters/pages/${chapterId(chapter)}", apiHeaders())

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = client.newCall(pageListRequest(chapter))
        .asObservable()
        .doOnNext { response ->
            if (response.code == 403) {
                val body = runCatching { response.peekBody(1024 * 1024).string() }.getOrDefault("")
                val hasTurnstile = isTurnstileChallenge(response, body)
                response.close()
                if (hasTurnstile || hasTurnstileChallenge(chapter)) {
                    throw Exception(CLOUDFLARE_VERIFY_MESSAGE)
                }
                throw Exception("HTTP error 403")
            }
            if (!response.isSuccessful) {
                response.close()
                throw Exception("HTTP error ${response.code}")
            }
        }
        .map(::pageListParse)

    override fun pageListParse(response: Response): List<Page> {
        val result = decryptIfNeeded(response)

        return result.pages.mapIndexed { index, page ->
            val rawUrl = page.url.replace("_credit", "").trimStart('/')

            if (rawUrl.startsWith("comics/") || rawUrl.startsWith("teams/")) {
                val key = page.key
                val url = "$dbUrl/storage/v1/object/public/yuri-garden-store/$rawUrl"
                    .toHttpUrl().newBuilder().apply {
                        if (!key.isNullOrEmpty()) {
                            fragment("KEY=$key")
                        }
                    }.build().toString()

                Page(index, imageUrl = url)
            } else {
                val url = rawUrl.toHttpUrlOrNull()?.toString() ?: rawUrl
                Page(index, imageUrl = url)
            }
        }
    }

    private fun decryptIfNeeded(response: Response): ChapterDetail {
        val body = response.body.string()

        // Check if the response is encrypted
        return if (body.contains("\"encrypted\"")) {
            val encrypted = body.parseAs<EncryptedResponse>()
            if (encrypted.encrypted && !encrypted.data.isNullOrEmpty()) {
                val decrypted = CryptoAES.decrypt(encrypted.data, AES_PASSWORD)
                decrypted.parseAs<ChapterDetail>()
            } else {
                body.parseAs<ChapterDetail>()
            }
        } else {
            body.parseAs<ChapterDetail>()
        }
    }

    private fun hasTurnstileChallenge(chapter: SChapter): Boolean {
        val urls = listOfNotNull(resolveReaderUrl(chapter), getChapterUrl(chapter)).distinct()

        return urls.any { url ->
            runCatching {
                client.newCall(GET(url, headers)).execute().use { response ->
                    val body = runCatching { response.body.string() }.getOrDefault("")
                    isTurnstileChallenge(response, body)
                }
            }.getOrDefault(false)
        }
    }

    private fun isTurnstileChallenge(response: Response, body: String): Boolean = response.header("cf-mitigated")?.equals("challenge", ignoreCase = true) == true ||
        hasTurnstileElement(body) ||
        body.contains("/cdn-cgi/challenge-platform", ignoreCase = true) ||
        body.contains("Just a moment", ignoreCase = true)

    private fun hasTurnstileElement(html: String): Boolean {
        if (html.isBlank()) return false

        val document = Jsoup.parse(html)
        return document.selectFirst(
            "div.cf-turnstile, " +
                "input[name=cf-turnstile-response], " +
                "iframe[src*=challenges.cloudflare.com], " +
                "form#challenge-form, " +
                "#cf-challenge-running, " +
                "#challenge-stage",
        ) != null ||
            html.contains("cf-turnstile", ignoreCase = true) ||
            html.contains("challenges.cloudflare.com/turnstile", ignoreCase = true)
    }

    private fun resolveReaderUrl(chapter: SChapter): String? = runCatching {
        val chapterId = chapterId(chapter)
        client.newCall(GET("$apiUrl/chapters/$chapterId", apiHeaders())).execute().use { response ->
            if (!response.isSuccessful) return@use null

            val body = response.body.string()
            val comicId = COMIC_ID_REGEX.find(body)?.groupValues?.getOrNull(1) ?: return@use null
            "$baseUrl/comic/$comicId/$chapterId"
        }
    }.getOrNull()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // =============================== Related ================================

    // disable suggested mangas on Komikku due to heavy rate limit
    override val disableRelatedMangasBySearch = true

    override fun relatedMangaListRequest(manga: SManga) = GET("$apiUrl/comics/related/${mangaId(manga)}", apiHeaders())

    override fun relatedMangaListParse(response: Response): List<SManga> {
        val result = response.parseAs<List<Comic>>()

        return result.map { comic ->
            SManga.create().apply {
                url = "/comic/${comic.id}"
                title = comic.title
                thumbnail_url = comic.thumbnail?.toThumbnailUrl()
            }
        }
    }

    // ============================== Helpers ================================

    private fun currentPage(response: Response): Int {
        val url = response.request.url
        return url.queryParameter("page")?.toIntOrNull() ?: 1
    }

    private fun String.toThumbnailUrl(): String = if (startsWith("http")) this else "$dbUrl/storage/v1/object/public/yuri-garden-store/$this"

    // ============================== Peferences ================================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_R18
            title = "Hiển thị nội dung R18"
            summary = "Bật để hiển thị truyện có nội dung người lớn (18+)"
            setDefaultValue(PREF_SHOW_R18_DEFAULT)
        }.also(screen::addPreference)
    }

    private val allowR18: Boolean
        get() = preferences.getBoolean(PREF_SHOW_R18, PREF_SHOW_R18_DEFAULT)

    companion object {
        private const val LIMIT = 15
        private const val AES_PASSWORD = "FYgicJ8oFdIYfgLv"
        private const val CLOUDFLARE_VERIFY_MESSAGE = "Mở webview để xác minh cloudflare cho chương này"
        private val COMIC_ID_REGEX = """"comic"\s*:\s*\{\s*"id"\s*:\s*(\d+)""".toRegex()
        private const val PREF_SHOW_R18 = "pref_show_r18"
        private const val PREF_SHOW_R18_DEFAULT = false
    }
}
