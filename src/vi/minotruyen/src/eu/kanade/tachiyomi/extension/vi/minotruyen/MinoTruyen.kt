package eu.kanade.tachiyomi.extension.vi.minotruyen

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.lib.cryptoaes.CryptoAES
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MinoTruyen(
    override val name: String,
    private val category: String,
) : HttpSource() {

    override val baseUrl = "https://minotruyenv5.xyz"

    private val apiUrl = "https://api.cloudkk.art/api"

    override val lang = "vi"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val apiHeaders by lazy { headersBuilder().add("Origin", baseUrl).build() }

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(apiUrl.toHttpUrl(), 3)
        .addInterceptor(MinoImageInterceptor())
        .build()

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/books/side-home".toHttpUrl().newBuilder()
            .addQueryParameter("category", category)
            .build()
        return GET(url, apiHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SideHomeResponse>()
        val mangaList = result.topBooksView.map { book ->
            SManga.create().apply {
                url = "/books/${book.bookId}"
                title = book.title.trim()
                thumbnail_url = resolveThumbnailUrl(book.covers.firstOrNull()?.url)
                status = parseStatus(book.status)
            }
        }
        return MangasPage(mangaList, false)
    }

    private inline fun <reified T> Response.parseAs(): T = json.decodeFromString<T>(body.string())

    // =============================== Latest ===============================

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
        val mangaList = result.books.map { it.toSManga() }
        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val take = response.request.url.queryParameter("take")?.toIntOrNull() ?: 24
        val hasNextPage = result.countBook?.let { currentPage * take < it } ?: mangaList.isNotEmpty()
        return MangasPage(mangaList, hasNextPage)
    }

    private fun Book.toSManga() = SManga.create().apply {
        url = "/books/$bookId"
        title = this@toSManga.title.trim()
        thumbnail_url = resolveThumbnailUrl(covers.firstOrNull()?.url)
        status = parseStatus(this@toSManga.status)
    }

    // =============================== Search ===============================

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

    // =============================== Details ==============================

    override fun mangaDetailsRequest(manga: SManga): Request {
        val bookId = manga.url.substringAfterLast("/")
        return GET("$apiUrl/books/$bookId", apiHeaders)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/$category${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<BookDetailResponse>()
        val book = result.book
        return SManga.create().apply {
            url = "/books/${book.bookId}"
            title = book.title.trim()
            thumbnail_url = resolveThumbnailUrl(book.covers.firstOrNull()?.url)
            author = book.author
            description = book.description
            genre = book.tags.joinToString { it.tag.name }
            status = parseStatus(book.status)
        }
    }

    private fun parseStatus(status: Int?): Int = when (status) {
        1 -> SManga.ONGOING
        2 -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

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
        return result.chapters.map { chapter ->
            SChapter.create().apply {
                val bookId = chapter.bookId
                val chapterNum = chapter.chapterNumber.toString().removeSuffix(".0")
                url = "/books/$bookId/$chapterNum"
                name = chapter.num
                date_upload = parseDate(chapter.createdAt)
            }
        }
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return try {
            dateFormat.parse(dateStr)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl/$category${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()

        val encrypted = ENCRYPTED_DATA_REGEX.find(html)?.groupValues?.get(1)
            ?: throw Exception("Could not find encrypted chapter data")

        val encData = encrypted.substringAfter(":")

        val decrypted = CryptoAES.decrypt(encData, AES_KEY)
        if (decrypted.isBlank()) {
            throw Exception("Failed to decrypt chapter data")
        }

        val servers = json.decodeFromString<List<ChapterServer>>(decrypted)

        val selectedServer = selectImageServer(servers)
            ?: throw Exception("No image server found")
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

    private fun resolveThumbnailUrl(url: String?): String? {
        val normalized = url
            ?.takeIf { it.isNotBlank() }
            ?.let(::normalizeImageUrl)
            ?: return null

        val parsed = normalized.toHttpUrlOrNull() ?: return normalized
        if (!parsed.host.contains("ibyteimg.com", ignoreCase = true)) return normalized
        if (parsed.encodedPath.contains("~tplv-", ignoreCase = true)) return normalized
        if (!parsed.encodedPath.startsWith("/obj/")) return normalized

        val host = parsed.host.replace(IBYTE_AD_MARKER, IBYTE_LP_MARKER)
        val objectPath = parsed.encodedPath.removePrefix("/obj/")
        return "https://$host/$objectPath$IBYTE_THUMBNAIL_SUFFIX"
    }

    private fun decodeDrmMap(drmData: String): List<StripInfo> {
        val encrypted = runCatching {
            Base64.decode(drmData, Base64.DEFAULT)
        }.getOrElse {
            return emptyList()
        }

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

    companion object {
        private const val AES_KEY = "GCERKSmf28E6nWwrnR8Lz4f7TacKpzMy7aK0rxSB"
        private val ENCRYPTED_DATA_REGEX = Regex("""([a-f0-9]{32}:U2FsdGVk[A-Za-z0-9+/=]+)""")
        private const val DRM_XOR_KEY = "3141592653589793"
        private const val DRM_MAP_PREFIX = "#mino-v1|"
        private const val DRM_FRAGMENT_PREFIX = "mino:"
        private const val IBYTE_AD_MARKER = "-ad-"
        private const val IBYTE_LP_MARKER = "-lp-"
        private const val IBYTE_THUMBNAIL_SUFFIX = "~tplv-375lmtcpo0-resize:200:200.webp"

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}

data class StripInfo(
    val destY: Int,
    val height: Int,
)
