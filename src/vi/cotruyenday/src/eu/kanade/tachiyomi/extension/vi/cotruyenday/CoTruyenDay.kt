package eu.kanade.tachiyomi.extension.vi.cotruyenday

import android.util.Base64
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.aesCbc
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class CoTruyenDay : KeiSource() {
    private val bootstrapApi = "https://api.truyenday3.work/api.php"

    private val aesKey = Base64.decode("SGVsbG8gQUVTIDI1NiBTYW1wbGUgS2V5IEZvciBZb3U=", Base64.DEFAULT)

    private var cachedApiBase: String? = null

    private suspend fun apiBase(): String {
        cachedApiBase?.let { return it }
        val endpoint = client.get(bootstrapApi, Fetch.XHR).use {
            it.parseAs<BootstrapResponse>()
        }.data.endpointApi
        return "$endpoint/api".also { cachedApiBase = it }
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(3)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val res = client.get("${apiBase()}/manga/list-pin?page=$page", Fetch.XHR)
            .use { it.parseAs<ListResponse>() }
        val manga = res.data.comics.map { it.toSManga() }
        return MangasPage(manga, res.data.page < res.data.totalPage)
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = mangaListPage(page)

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "${apiBase()}/manga/list".toHttpUrl().newBuilder()
                .addQueryParameter("search", query)
                .addQueryParameter("page", page.toString())
                .build()
            return mangaListPage(url)
        }

        val genreIds = filters.firstInstanceOrNull<GenreFilter>()
            ?.state?.filter { it.state }?.map { it.id }.orEmpty()
        if (genreIds.isNotEmpty()) {
            val url = "${apiBase()}/manga/list".toHttpUrl().newBuilder().apply {
                genreIds.forEachIndexed { i, id -> addQueryParameter("genre_ids[$i]", id) }
                addQueryParameter("page", page.toString())
            }.build()
            return mangaListPage(url)
        }

        return mangaListPage(page)
    }

    private suspend fun mangaListPage(page: Int): MangasPage = mangaListPage("${apiBase()}/manga/list?page=$page".toHttpUrl())

    private suspend fun mangaListPage(url: HttpUrl): MangasPage {
        val res = client.get(url, Fetch.XHR).use { it.parseAs<ListResponse>() }
        val manga = res.data.comics.map { it.toSManga() }
        return MangasPage(manga, res.data.page < res.data.totalPage)
    }

    // ============================= Manga URL ==============================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/truyen-tranh/${manga.memo["mangaSlug"]?.stringOrNull ?: "x-${manga.url}"}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || !url.pathSegments.contains("truyen-tranh")) {
            return null
        }
        val slug = url.pathSegments.firstOrNull { it.startsWith("chapter-") }
            ?.let { url.pathSegments[url.pathSegments.indexOf(it) - 1] }
            ?: url.pathSegments.last()
        val id = slug.substringAfterLast("-").toLongOrNull() ?: return null
        val manga = SManga.create().apply {
            this.url = id.toString()
            memo = buildJsonObject { put("mangaSlug", slug) }
        }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // =========================== Manga Details ============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val base = apiBase()
        val detailsDeferred = async {
            if (fetchDetails) {
                client.get("$base/manga/${manga.url}", Fetch.XHR)
                    .use { it.parseAs<DetailResponse>() }.data.toSManga(manga.url)
            } else {
                manga
            }
        }
        val chaptersDeferred = async {
            if (fetchChapters) fetchAllChapters(base, manga.url) else emptyList()
        }
        val updatedManga = detailsDeferred.await()
        val chapterDtos = chaptersDeferred.await()
        val mangaSlug = updatedManga.memo["mangaSlug"]?.stringOrNull ?: "x-${manga.url}"
        val updatedChapters = if (fetchChapters) {
            chapterDtos.map { it.toSChapter(mangaSlug) }.sortedByDescending { it.chapter_number }
        } else {
            chapters
        }
        SMangaUpdate(manga = updatedManga, chapters = updatedChapters)
    }

    private suspend fun fetchAllChapters(base: String, mangaId: String): List<ChapterDto> = coroutineScope {
        suspend fun fetchPage(p: Int) = client
            .get("$base/chapter/list/$mangaId?page=$p", Fetch.XHR)
            .use { it.parseAs<ChapterListResponse>() }.data

        val first = fetchPage(1)
        val rest = (2..first.totalPage).map { p -> async { fetchPage(p) } }.awaitAll()
        (listOf(first) + rest).flatMap { it.comics }
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val data = client.get("${apiBase()}/chapter/${chapter.url}", Fetch.XHR)
            .use { it.parseAs<ChapterResponse>() }.data
        val urls = when {
            data.imageUrls.isNotEmpty() -> data.imageUrls
            data.encryptedImages != null -> decryptImageUrls(data.encryptedImages)
            else -> emptyList()
        }
        return urls.mapIndexed { i, u -> Page(i, imageUrl = u) }
    }

    private fun decryptImageUrls(encrypted: EncryptedDto): List<String> {
        val cipher = aesCbc(aesKey, Base64.decode(encrypted.iv, Base64.DEFAULT))
        val plain = Base64.decode(encrypted.encryptedData, Base64.DEFAULT).aesDecrypt(cipher)
        return plain.decodeToString().parseAs<List<String>>()
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val slug = chapter.memo["mangaSlug"]?.stringOrNull ?: "x"
        val num = chapter.memo["num"]?.stringOrNull ?: chapter.url
        return "$baseUrl/truyen-tranh/$slug/chapter-$num"
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("${apiBase()}/genre/list", Fetch.XHR)
        .use { it.parseAs<GenreListResponse>() }.data.toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreDto>>())
}
