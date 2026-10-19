package eu.kanade.tachiyomi.extension.zh.xipimanhua

import android.util.Base64
import eu.kanade.tachiyomi.source.model.Filter
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.math.BigInteger

@Source
abstract class XipiManhua : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 4) { it.host == API_HOST }

    override suspend fun getPopularManga(page: Int): MangasPage = mangaList(page, sort = "popular")

    override suspend fun getLatestUpdates(page: Int): MangasPage = mangaList(page, sort = "updated")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$API_URL/v1/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query.trim())
                .addQueryParameter("page", page.toString())
                .addQueryParameter("page_size", "20")
                .build()
            val data = client.get(url).parseData<SearchDto>()
            // The index holds a series once per slug alias, all with the same mid
            return MangasPage(data.data.map { it.toSManga() }.distinctBy { it.url }, data.page < data.totalPages)
        }
        return mangaList(
            page,
            sort = filters.firstInstanceOrNull<SortFilter>()?.value ?: "popular",
            category = filters.firstInstanceOrNull<CategoryFilter>()?.value,
            genre = filters.firstInstanceOrNull<GenreFilter>()?.value,
            status = filters.firstInstanceOrNull<StatusFilter>()?.value,
        )
    }

    private suspend fun mangaList(
        page: Int,
        sort: String,
        category: String? = null,
        genre: String? = null,
        status: String? = null,
    ): MangasPage {
        val url = "$API_URL/v1/mangas".toHttpUrl().newBuilder().apply {
            category?.ifEmpty { null }?.let { addQueryParameter("category", it) }
            genre?.ifEmpty { null }?.let { addQueryParameter("genre", it) }
            status?.ifEmpty { null }?.let { addQueryParameter("status", it) }
            addQueryParameter("sort", sort)
            addQueryParameter("page", page.toString())
            addQueryParameter("per_page", "18")
        }.build()
        val data = client.get(url).parseData<MangaListDto>()
        return MangasPage(data.items.map { it.toSManga() }, data.page < data.totalPages)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host && url.host != READER_HOST) return null
        val segments = url.pathSegments
        val mid = when {
            "works" in segments -> segments.getOrNull(segments.indexOf("works") + 1)?.substringBefore('-')
            "chapter" in segments -> segments.getOrNull(segments.indexOf("chapter") + 1)
                ?.let { if (it == "go") url.queryParameter("hid") else it }
                ?.let { hid -> runCatching { encode(decode(hid.substringBefore('-')).substringBefore("-c:")) }.getOrNull() }
            else -> null
        }
        // Links can carry anything; only a base64 "m:<id>" is a series
        if (mid.isNullOrEmpty() || runCatching { decode(mid) }.getOrNull()?.startsWith("m:") != true) return null
        val manga = SManga.create().apply { this.url = mid }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = if (fetchDetails) async { mangaDetails(manga.url) } else null
        val chapterList = if (fetchChapters) async { chapterList(manga.url) } else null
        SMangaUpdate(details?.await() ?: manga, chapterList?.await() ?: chapters)
    }

    private suspend fun mangaDetails(mid: String): SManga {
        val url = "$API_URL/v1/manga".toHttpUrl().newBuilder()
            .addQueryParameter("mid", mid)
            .build()
        return client.get(url).parseData<MangaDto>().toSManga().apply { this.url = mid }
    }

    private suspend fun chapterList(mid: String): List<SChapter> = coroutineScope {
        val first = chapterPage(mid, 1)
        val rest = (2..first.totalPages).map { page -> async { chapterPage(mid, page) } }.awaitAll()
        (listOf(first) + rest).flatMap { it.items }.map { it.toSChapter() }
    }

    private suspend fun chapterPage(mid: String, page: Int): ChapterListDto {
        val url = "$API_URL/v1/manga/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("mid", mid)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", "50")
            .addQueryParameter("order", "desc")
            .build()
        return client.get(url).parseData()
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/works/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "https://$READER_HOST/chapter/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        // Chapter hid = b64("m:<manga id>-c:<chapter id>")-<suffix>; the API takes b64("c:<chapter id>")-<suffix>
        val ids = decode(chapter.url.substringBefore('-'))
        val apiHid = encode(ids.substringAfter('-')) + "-" + chapter.url.substringAfter('-')
        val url = "$API_URL/v2/chapter".toHttpUrl().newBuilder()
            .addQueryParameter("hid", apiHid)
            .build()
        val data = client.get(url).parseData<ChapterPagesDto>()

        val images = decodeImages(data.images).toMutableList()
        if (images.isNotEmpty()) decoyIndex(images.size, data.orderId, data.sid)?.let(images::removeAt)

        // Line 9 is served from the reader's "s1" image hosts
        val imageBase = if (data.line == 9) IMAGE_URL_S1 else IMAGE_URL
        return images.mapIndexed { index, path -> Page(index, imageUrl = imageBase + path) }
    }

    // The site reader's page list decoding (/assets/runtime/chapter-decoder.js on the reader host):
    // "qM9" head "Vx" mid "pL0" tail "Z7", with tail = n / 3 and head = (n - tail) / 2 of the n payload chars
    private fun decodeImages(encoded: String): List<String> {
        val body = encoded.removePrefix("qM9").removeSuffix("Z7")
        val tailLength = (body.length - 5) / 3
        val headLength = (body.length - 5 - tailLength) / 2
        val head = body.take(headLength)
        val mid = body.substring(headLength + 2, body.length - 3 - tailLength)
        val tail = body.takeLast(tailLength)
        val base64 = (tail + head + mid).chunked(7)
            .mapIndexed { index, block -> if (index % 2 == 1) block.reversed() else block }
            .joinToString("")
            .map { DECODED_ALPHABET[ENCODED_ALPHABET.indexOf(it)] }
            .joinToString("")
        return decode(base64).parseAs()
    }

    // The site reader drops one entry of the list before showing the pages; BigInteger like its BigInt maths
    private fun decoyIndex(size: Int, orderId: Long, sid: Long): Int? {
        val key = (sid.toBigInteger() * DECOY_SID_FACTOR).xor(size.toBigInteger() * DECOY_SIZE_FACTOR)
            .mod(size.toBigInteger())
        return orderId.toBigInteger().xor(key).takeIf { it in BigInteger.ZERO..<size.toBigInteger() }?.toInt()
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("搜尋文字時不使用篩選"),
        SortFilter(),
        CategoryFilter(),
        GenreFilter(),
        StatusFilter(),
    )

    private fun decode(base64: String): String = String(Base64.decode(base64, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))

    private fun encode(text: String): String = Base64.encodeToString(text.toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    private inline fun <reified T> Response.parseData(): T = parseAs<ApiResponse<T>>().let { it.data ?: throw Exception("API error: ${it.message}") }

    companion object {
        private const val API_HOST = "hipapi1.s3file.top"
        private const val API_URL = "https://$API_HOST"
        private const val READER_HOST = "reader.hipmh.top"
        private const val IMAGE_URL = "https://hip-tx-1.s3imgs.top"
        private const val IMAGE_URL_S1 = "https://hip-tx-s1.s3imgs.top"
        private const val ENCODED_ALPHABET = "_-9876543210abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val DECODED_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        private val DECOY_SID_FACTOR = BigInteger.valueOf(0x9E3779B1L)
        private val DECOY_SIZE_FACTOR = BigInteger.valueOf(0x85EBCA6BL)
    }
}
