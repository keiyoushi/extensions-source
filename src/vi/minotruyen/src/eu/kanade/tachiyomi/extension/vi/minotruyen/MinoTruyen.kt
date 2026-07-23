package eu.kanade.tachiyomi.extension.vi.minotruyen

import android.util.Base64
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.lib.cryptoaes.CryptoAES
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.nio.charset.StandardCharsets

@Source
abstract class MinoTruyen : KeiSource() {

    private val category: String
        get() = when {
            name.contains("Comics") -> "comics"
            name.contains("Hentai") -> "hentai"
            else -> "manga"
        }

    private var resolvedApiUrl: String? = null

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addInterceptor(ImageInterceptor())
        rateLimit(3)
    }

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "${getApiUrl()}/books".toHttpUrl().newBuilder()
            .addQueryParameter("take", "24")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("category", category)
            .build()
        val result = client.get(url).parseAs<BooksResponse>()
        return MangasPage(result.toSMangaList(baseUrl), result.hasNextPage(page))
    }

    // ============================== Latest ===============================

    override val supportsLatest = false

    override suspend fun getLatestUpdates(page: Int): MangasPage = getPopularManga(page)

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "${getApiUrl()}/books".toHttpUrl().newBuilder()
            .addQueryParameter("take", "24")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("category", category)

        if (query.isNotEmpty()) {
            url.addQueryParameter("q", query)
        }

        filters.firstInstanceOrNull<GenreFilter>()
            ?.selectedValues()
            ?.forEach { url.addQueryParameter("includeTags", it) }

        val result = client.get(url.build()).parseAs<BooksResponse>()
        val mangaList = result.toSMangaList(baseUrl)
        return MangasPage(mangaList, result.hasNextPage(page))
    }

    // ============================== Details ==============================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/$category${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/$category${chapter.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val categoryIndex = url.pathSegments.indexOfFirst { it in categories }
        if (categoryIndex == -1 || url.pathSegments.getOrNull(categoryIndex + 1) != "books") return null

        val bookId = url.pathSegments.getOrNull(categoryIndex + 2)?.toIntOrNull() ?: return null
        val manga = SManga.create().apply { setUrlWithoutDomain("/books/$bookId") }
        return fetchMangaUpdate(manga, emptyList(), true, false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val bookId = manga.url.substringAfterLast("/")
        val apiUrl = getApiUrl()
        val mangaDeferred = if (fetchDetails) {
            async {
                client.get("$apiUrl/books/$bookId")
                    .parseAs<BookDetailResponse>()
                    .toSManga(baseUrl)
            }
        } else {
            null
        }
        val chaptersDeferred = if (fetchChapters) {
            async {
                val url = "$apiUrl/books/$bookId/chapters".toHttpUrl().newBuilder()
                    .addQueryParameter("order", "desc")
                    .build()
                client.get(url).parseAs<ChaptersResponse>().toSChapterList(bookId)
            }
        } else {
            null
        }

        SMangaUpdate(
            manga = mangaDeferred?.await() ?: manga,
            chapters = chaptersDeferred?.await() ?: chapters,
        )
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl/$category${chapter.url}").asJsoup()
        val chapterId = chapter.url.substringAfterLast('/').toIntOrNull()
        val embeddedPages = document.extractNextJs<ReaderChapter>()
            ?.takeIf { chapterId == null || it.chapterId == chapterId }
            ?.images
            .orEmpty()
            .sortedBy { it.order }
            .mapNotNull { selectReaderServer(it.servers) }

        val pages = embeddedPages.ifEmpty {
            val encrypted = encryptedDataRegex.find(document.html())?.groupValues?.get(1)
                ?: return emptyList()

            val encData = encrypted.substringAfter(":")

            val decrypted = CryptoAES.decrypt(encData, aesKey)
            if (decrypted.isBlank()) return emptyList()

            val servers = decrypted.parseAs<List<ChapterServer>>()

            selectLegacyServer(servers)?.content?.map { page ->
                ReaderPage(page.imageUrl, page.drmData)
            } ?: return emptyList()
        }

        return pages.mapIndexed { index, page ->
            val normalizedImageUrl = normalizeImageUrl(page.imageUrl)
            val imageUrl = page.drmData
                ?.let { decodeDrmMap(it) }
                ?.takeIf { it.isNotEmpty() }
                ?.let { strips ->
                    val map = strips.joinToString(",") { "${it.destY}-${it.height}" }
                    normalizedImageUrl.toHttpUrl().newBuilder()
                        .fragment("$drmFragmentPrefix$map")
                        .build()
                        .toString()
                }
                ?: normalizedImageUrl

            Page(index, imageUrl = imageUrl)
        }
    }

    private fun selectReaderServer(servers: List<ReaderPage>): ReaderPage? = servers.firstOrNull { page ->
        val host = normalizeImageUrl(page.imageUrl).toHttpUrlOrNull()?.host.orEmpty()
        host.isNotEmpty() && !host.contains("ibyteimg.com", ignoreCase = true)
    } ?: servers.firstOrNull()

    private fun selectLegacyServer(servers: List<ChapterServer>): ChapterServer? {
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

        val key = drmXorKey.toByteArray(StandardCharsets.US_ASCII)
        val plainBytes = ByteArray(encrypted.size)
        for (i in encrypted.indices) {
            plainBytes[i] = (encrypted[i].toInt() xor key[i % key.size].toInt()).toByte()
        }

        val plain = plainBytes.toString(StandardCharsets.UTF_8)
        if (!plain.startsWith(drmMapPrefix)) {
            return emptyList()
        }

        return plain.removePrefix(drmMapPrefix)
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

    // ============================== Filters ==============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val url = "${getApiUrl()}/books/tags".toHttpUrl().newBuilder()
            .addQueryParameter("take", "50")
            .addQueryParameter("category", category)
            .build()
        return client.get(url).parseAs<TagsResponse>().data.tags.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<TagOption>>())

    // ============================= Utilities =============================

    private suspend fun getApiUrl(): String {
        resolvedApiUrl?.let { return it }

        val resolved = runCatching {
            client.get("$apiUrlDefault/books?take=1&category=$category").close()
            apiUrlDefault
        }.getOrElse {
            val document = client.get(baseUrl).asJsoup()
            document.select("script[src*=chunks]").firstNotNullOfOrNull { script ->
                runCatching {
                    val text = client.get(script.absUrl("src")).use { it.body.string() }
                    apiUrlRegex.find(text)?.groupValues?.get(1)?.let { "$it/api" }
                }.getOrNull()
            } ?: apiUrlDefault
        }

        resolvedApiUrl = resolved
        return resolved
    }

    private val categories = setOf("manga", "comics", "hentai")
    private val apiUrlRegex = Regex("""NEXT_PUBLIC_API_URL\W+"(https?://[^"]+)"""")
    private val apiUrlDefault = "https://api.cloudkk-v1.xyz/api"
    private val aesKey = "GCERKSmf28E6nWwrnR8Lz4f7TacKpzMy7aK0rxSB"
    private val encryptedDataRegex = Regex("""([a-f0-9]{32}:U2FsdGVk[A-Za-z0-9+/=]+)""")
    private val drmXorKey = "3141592653589793"
    private val drmMapPrefix = "#mino-v1|"
    private val drmFragmentPrefix = "mino:"
}

data class StripInfo(
    val destY: Int,
    val height: Int,
)
