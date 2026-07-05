package eu.kanade.tachiyomi.extension.vi.minotruyen

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.lib.cryptoaes.CryptoAES
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import java.nio.charset.StandardCharsets

@Source
abstract class MinoTruyen : HttpSource() {

    private val category: String
        get() = when {
            name.contains("Comics") -> "comics"
            name.contains("Hentai") -> "hentai"
            else -> "manga"
        }

    private val apiUrlHost by lazy { apiUrl.toHttpUrl().host }

    override val supportsLatest = true

    private val apiUrl by lazy { resolveApiUrl() }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val apiHeaders by lazy { headersBuilder().add("Origin", baseUrl).build() }

    override val client by lazy {
        network.client.newBuilder()
            .addInterceptor(ImageInterceptor())
            .rateLimit(3) { it.host == apiUrlHost }
            .build()
    }

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/books/side-home".toHttpUrl().newBuilder()
            .addQueryParameter("category", category)
            .build()
        return GET(url, apiHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SideHomeResponse>()
        return MangasPage(result.toSMangaList(baseUrl), false)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/books".toHttpUrl().newBuilder()
            .addQueryParameter("take", "24")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("category", category)
            .build()
        return GET(url, apiHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<BooksResponse>()
        val mangaList = result.toSMangaList(baseUrl)
        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val take = response.request.url.queryParameter("take")?.toIntOrNull() ?: 24
        val hasNextPage = result.countBook?.let { currentPage * take < it } ?: mangaList.isNotEmpty()
        return MangasPage(mangaList, hasNextPage)
    }

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/books".toHttpUrl().newBuilder()
            .addQueryParameter("take", "24")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("category", category)

        if (query.isNotBlank()) {
            url.addQueryParameter("q", query)
        }

        return GET(url.build(), apiHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    // ============================== Details ==============================

    override fun mangaDetailsRequest(manga: SManga): Request {
        val bookId = manga.url.substringAfterLast("/")
        return GET("$apiUrl/books/$bookId", apiHeaders)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/$category${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<BookDetailResponse>()
        return result.toSManga(baseUrl)
    }

    // ============================= Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request {
        val bookId = manga.url.substringAfterLast("/")
        val url = "$apiUrl/chapters/$bookId".toHttpUrl().newBuilder()
            .addQueryParameter("order", "desc")
            .addQueryParameter("take", "5000")
            .build()
        return GET(url, apiHeaders)
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/$category${chapter.url}"

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<ChaptersResponse>()
        return result.toSChapterList()
    }

    // =============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl/$category${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()

        val encrypted = ENCRYPTED_DATA_REGEX.find(html)?.groupValues?.get(1)
            ?: return emptyList()

        val encData = encrypted.substringAfter(":")

        val decrypted = CryptoAES.decrypt(encData, AES_KEY)
        if (decrypted.isBlank()) return emptyList()

        val servers = decrypted.parseAs<List<ChapterServer>>()

        val selectedServer = selectImageServer(servers) ?: return emptyList()
        val pages = selectedServer.content

        return pages.mapIndexed { index, page ->
            val normalizedImageUrl = normalizeImageUrl(page.imageUrl)
            val imageUrl = page.drmData
                ?.let { decodeDrmMap(it) }
                ?.takeIf { it.isNotEmpty() }
                ?.let { strips ->
                    val map = strips.joinToString(",") { "${it.destY}-${it.height}" }
                    normalizedImageUrl.toHttpUrl().newBuilder()
                        .fragment("$DRM_FRAGMENT_PREFIX$map")
                        .build()
                        .toString()
                }
                ?: normalizedImageUrl

            Page(index, imageUrl = imageUrl)
        }
    }

    private fun selectImageServer(servers: List<ChapterServer>): ChapterServer? {
        val candidates = servers.filter { it.content.isNotEmpty() }
        if (candidates.isEmpty()) return null

        return candidates.firstOrNull { server ->
            server.content.any { page ->
                val host = normalizeImageUrl(page.imageUrl).toHttpUrlOrNull()?.host.orEmpty()
                host.isNotEmpty() && !host.contains("ibyteimg.com", ignoreCase = true)
            }
        } ?: candidates.first()
    }

    private fun normalizeImageUrl(url: String): String = when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "$baseUrl$url"
        else -> url
    }

    private fun decodeDrmMap(drmData: String): List<StripInfo> {
        val encrypted = Base64.decode(drmData, Base64.DEFAULT)

        val key = DRM_XOR_KEY.toByteArray(StandardCharsets.US_ASCII)
        val plainBytes = ByteArray(encrypted.size)
        for (i in encrypted.indices) {
            plainBytes[i] = (encrypted[i].toInt() xor key[i % key.size].toInt()).toByte()
        }

        val plain = plainBytes.toString(StandardCharsets.UTF_8)
        if (!plain.startsWith(DRM_MAP_PREFIX)) {
            return emptyList()
        }

        return plain.removePrefix(DRM_MAP_PREFIX)
            .split('|')
            .mapNotNull { token ->
                val dy = token.substringBefore('-').toIntOrNull()
                val height = token.substringAfter('-', "").toIntOrNull()
                if (dy == null || height == null || height <= 0 || dy < 0) {
                    null
                } else {
                    StripInfo(destY = dy, height = height)
                }
            }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================= Utilities =============================

    private fun resolveApiUrl(): String {
        val baseClient = network.client

        try {
            val request = Request.Builder()
                .url("$API_URL_DEFAULT/books?take=1&category=$category")
                .head()
                .headers(headers)
                .build()
            baseClient.newCall(request).execute().use { }
            return API_URL_DEFAULT
        } catch (_: Exception) {}

        try {
            val response = baseClient.newCall(GET(baseUrl, headers)).execute()
            val doc = response.asJsoup()

            for (script in doc.select("script[src*=chunks]")) {
                try {
                    val text = baseClient.newCall(GET(script.absUrl("src"), headers)).execute().use { it.body.string() }
                    API_URL_REGEX.find(text)?.groupValues?.get(1)?.let {
                        return "$it/api"
                    }
                } catch (_: Exception) {
                    continue
                }
            }
        } catch (_: Exception) {}

        return API_URL_DEFAULT
    }

    companion object {
        private val API_URL_REGEX = Regex("""NEXT_PUBLIC_API_URL\W+"(https?://[^"]+)"""")
        private const val API_URL_DEFAULT = "https://api.cloudkk-v1.xyz/api"
        private const val AES_KEY = "GCERKSmf28E6nWwrnR8Lz4f7TacKpzMy7aK0rxSB"
        private val ENCRYPTED_DATA_REGEX = Regex("""([a-f0-9]{32}:U2FsdGVk[A-Za-z0-9+/=]+)""")
        private const val DRM_XOR_KEY = "3141592653589793"
        private const val DRM_MAP_PREFIX = "#mino-v1|"
        private const val DRM_FRAGMENT_PREFIX = "mino:"
    }
}

data class StripInfo(
    val destY: Int,
    val height: Int,
)
