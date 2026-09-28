package eu.kanade.tachiyomi.extension.vi.damconuong

import android.util.Base64
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Instant

@Source
abstract class DamCoNuong : KeiSource() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(5)
        addInterceptor(UnscrambleInterceptor)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = fetchMangaList(page, popularSort)

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchMangaList(page, latestSort)

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: latestSort
        val status = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart() ?: defaultStatus
        val searchType = filters.firstInstanceOrNull<SearchTypeFilter>()?.toUriPart() ?: "name"
        val genres = filters.firstInstanceOrNull<GenreFilter>()
            ?.state
            ?.filter { it.state }
            ?.joinToString(",") { it.id.toString() }
            ?.takeIf { it.isNotEmpty() }

        return fetchMangaList(
            page = page,
            sort = sort,
            status = status,
            searchField = searchType,
            query = query.takeIf { it.isNotBlank() },
            genres = genres,
        )
    }

    private suspend fun fetchMangaList(
        page: Int,
        sort: String,
        status: String = defaultStatus,
        searchField: String? = null,
        query: String? = null,
        genres: String? = null,
    ): MangasPage {
        val url = "$API_URL/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", pageSize.toString())
            .addQueryParameter("sort", sort)
            .addQueryParameter("filter[status]", status)
            .apply {
                if (searchField != null && query != null) {
                    addQueryParameter("filter[$searchField]", query)
                }
                if (genres != null) {
                    addQueryParameter("filter[accept_genres]", genres)
                }
            }
            .build()

        val response = client.get(url).parseAs<MangaListResponse>()

        return MangasPage(
            mangas = response.data.map { it.toSManga() },
            hasNextPage = response.meta.currentPage < response.meta.lastPage,
        )
    }

    // =============================== Details ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = url.mangaSlug() ?: return null

        return fetchMangaDetails(SManga.create().apply { this.url = slug })
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = if (fetchDetails) async { fetchMangaDetails(manga) } else null
        val chapterList = if (fetchChapters) async { fetchChapterList(manga) } else null

        SMangaUpdate(
            manga = details?.await() ?: manga,
            chapters = chapterList?.await() ?: chapters,
        )
    }

    private suspend fun fetchMangaDetails(manga: SManga): SManga {
        val details = client.get("$API_URL/mangas/${manga.url}?include=genres,artist,author")
            .parseAs<MangaResponse>()
            .data

        return SManga.create().apply {
            url = manga.url
            title = details.name
            thumbnail_url = details.coverUrl
            author = details.author?.name
            artist = details.artist?.name
            genre = details.genres.joinToString { it.name }.ifEmpty { null }
            status = when (details.status) {
                STATUS_COMPLETED -> SManga.COMPLETED
                STATUS_ONGOING -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
            description = details.pilot
                ?.let { Jsoup.parseBodyFragment(it, baseUrl).text() }
                ?.ifEmpty { null }
        }
    }

    // ============================== Chapters ==============================

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var page = 1
        var lastPage: Int

        do {
            val response = client.get("$API_URL/mangas/${manga.url}/chapters?page=$page&sort=desc")
                .parseAs<ChapterListResponse>()

            chapters += response.data.map { it.toSChapter(manga.url) }
            lastPage = response.meta.lastPage
            page++
        } while (page <= lastPage)

        return chapters
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val segments = chapter.url.split("/", limit = 2)
        val mangaSlug = segments.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return emptyList()
        val chapterSlug = segments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return emptyList()

        val path = "$mangaSlug/$chapterSlug"
        val token = buildToken(path)
        val payload = client.get("$API_URL/mangas/$mangaSlug/chapters/$chapterSlug/pages?_=$token")
            .parseAs<EncryptedPagesResponse>()
        val pages = decryptPages(payload.e, token, path)

        return pages.p.mapIndexed { index, imageUrl ->
            val scrambleKey = pages.s.getOrNull(index)
            Page(index, imageUrl = if (scrambleKey.isNullOrEmpty()) imageUrl else imageUrl.toScrambledUrl(scrambleKey))
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/truyen/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/truyen/${chapter.url}"

    // =============================== Crypto ===============================

    private val secretKey: ByteArray by lazy {
        Base64.decode(SECRET, Base64.URL_SAFE)
    }

    private val tokenKey: ByteArray by lazy { hmacSha256(secretKey, "tok".toByteArray()) }

    private val encryptionKey: ByteArray by lazy { hmacSha256(secretKey, "enc".toByteArray()) }

    private fun buildToken(path: String): String {
        val signature = hmacSha256(tokenKey, path.toByteArray()).copyOf(TOKEN_SIGNATURE_SIZE)
        val token = byteArrayOf(TOKEN_VERSION) + signature

        return Base64.encodeToString(token, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun decryptPages(payload: String, token: String, path: String): PageListResponse {
        val blob = Base64.decode(payload, Base64.URL_SAFE)
        val iv = blob.copyOfRange(0, GCM_IV_SIZE)
        val ciphertext = blob.copyOfRange(GCM_IV_SIZE, blob.size)
        val key = hmacSha256(encryptionKey, token.toByteArray())

        val plaintext = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            updateAAD(path.toByteArray())
            doFinal(ciphertext)
        }

        return plaintext.decodeToString().parseAs<PageListResponse>()
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").apply {
        init(SecretKeySpec(key, "HmacSHA256"))
    }.doFinal(data)

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val genres = mutableListOf<GenreDto>()
        var page = 1
        var lastPage: Int

        do {
            val response = client.get("$API_URL/genres?per_page=100&page=$page").parseAs<GenreListResponse>()

            genres += response.data
            lastPage = response.meta.lastPage
            page++
        } while (page <= lastPage)

        return genres.sortedBy { it.name }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreDto>>())

    // =============================== Helpers ==============================

    private fun MangaDto.toSManga() = SManga.create().apply {
        url = slug
        title = name
        thumbnail_url = coverUrl
    }

    private fun ChapterDto.toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "$mangaSlug/$slug"
        name = this@toSChapter.name
        chapter_number = this@toSChapter.chapterNumber ?: -1f
        date_upload = Instant.tryParse(this@toSChapter.createdAt)
    }

    private fun HttpUrl.mangaSlug(): String? {
        if (host != baseUrl.toHttpUrl().host) return null
        if (pathSegments.firstOrNull() != "truyen") return null

        return pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    private fun String.toScrambledUrl(key: String): String = toHttpUrl()
        .newBuilder()
        .addQueryParameter(UnscrambleInterceptor.QUERY_PARAM, key)
        .build()
        .toString()

    private val latestSort = "-updated_at"
    private val popularSort = "-views"
    private val defaultStatus = "2,1"
    private val pageSize = 36

    private companion object {
        const val API_URL = "https://api.damconuong.pw/api/v1"
        const val SECRET = "YVdGuT8RjDWkeQjt7s7mv53smMpLrcKBuGMs8erg8Bs"
        const val TOKEN_VERSION: Byte = 1
        const val TOKEN_SIGNATURE_SIZE = 16
        const val GCM_IV_SIZE = 12
        const val GCM_TAG_BITS = 128
        const val STATUS_COMPLETED = 1
        const val STATUS_ONGOING = 2
    }
}
