package eu.kanade.tachiyomi.extension.ja.nicomanga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class Nicomanga : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2)
    }

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage = fetchMangaListPage(
        "$baseUrl/manga-list.html".toHttpUrl().newBuilder()
            .addQueryParameter("p", page.toString())
            .addQueryParameter("pr", "popular")
            .build(),
    )

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchMangaListPage(
        "$baseUrl/manga-list.html".toHttpUrl().newBuilder()
            .addQueryParameter("p", page.toString())
            .addQueryParameter("pr", "new")
            .build(),
    )

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val author = filters.firstInstanceOrNull<AuthorFilter>()?.state?.trim()

        // The site splits name (n) and author (a) search into separate parameters
        // (ANDing them yields "No Manga Found"), and the global search only fills
        // the plain query - so when the name search comes back empty, retry the
        // query as an author search.
        if (query.isNotBlank() && author.isNullOrBlank() && page == 1) {
            val byName = searchPage(page, query, null, filters)
            if (byName.mangas.isNotEmpty()) return byName

            val byAuthor = searchPage(page, null, query, filters)
            if (byAuthor.mangas.isNotEmpty()) return byAuthor

            return byName
        }

        return searchPage(page, query, author, filters)
    }

    private suspend fun searchPage(page: Int, name: String?, author: String?, filters: FilterList): MangasPage {
        val url = "$baseUrl/manga-list.html".toHttpUrl().newBuilder()
            .addQueryParameter("p", page.toString())

        if (!name.isNullOrBlank()) {
            url.addQueryParameter("n", name.trim())
        }

        if (!author.isNullOrBlank()) {
            url.addQueryParameter("a", author.trim())
        }

        filters.firstInstanceOrNull<SortFilter>()?.state?.let { state ->
            val sortables = arrayOf("last_update", "views", "post", "name")
            val prs = arrayOf("all", "popular", "new", "az")

            url.addQueryParameter("s", sortables[state.index])
            url.addQueryParameter("pr", prs[state.index])
            url.addQueryParameter("st", if (state.ascending) "ASC" else "DESC")
        }

        val genres = filters.firstInstanceOrNull<GenreList>()?.state
            ?.filter { it.state }
            ?.map { it.id }
            ?: emptyList()

        if (genres.isNotEmpty()) {
            url.addQueryParameter("g", genres.joinToString(","))
            val logicFilter = filters.firstInstanceOrNull<MatchingLogic>()
            url.addQueryParameter("gm", (logicFilter?.state ?: 1).toString())
        }

        return fetchMangaListPage(url.build())
    }

    // ========================== Details & chapters ========================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val payload = fetchChaoticPayload(getMangaUrl(manga).toHttpUrl())
        val details = payload.manga!!.toSManga(baseUrl)
        val chapterList = payload.chaptersList.orEmpty().map { it.toSChapter() }

        return SMangaUpdate(details, chapterList)
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val images = fetchChaoticPayload(getChapterUrl(chapter).toHttpUrl()).images.orEmpty()

        return images.mapIndexed { i, url ->
            Page(i, imageUrl = url)
        }
    }

    // ============================= Utilities =============================

    private suspend fun fetchMangaListPage(url: HttpUrl): MangasPage {
        val response = fetchDeferredData<ListResponse>(url)
        val mangas = parseListItems(response.items).map { it.toSManga() }

        return MangasPage(mangas, response.pagination?.hasNextPage == true)
    }

    private fun parseListItems(items: JsonElement?): List<NListManga> = when (items) {
        is JsonPrimitive -> {
            if (items.isString && items.content.isNotEmpty()) {
                decodeXorStream(items.content, LIST_PAYLOAD_KEY).parseAs()
            } else {
                emptyList()
            }
        }
        is JsonArray -> items.parseAs()
        else -> emptyList()
    }

    private suspend fun fetchChaoticPayload(url: HttpUrl): Payload {
        val response = fetchDeferredData<Payload>(url)
        return response.chaoticPayload
            ?.takeIf { it.isNotEmpty() }
            ?.let { decodeXorStream(it, DETAIL_PAYLOAD_KEY).parseAs<Payload>() }
            ?: response
    }

    private suspend inline fun <reified T> fetchDeferredData(url: HttpUrl): T {
        val payloadKey = client.get(url, Fetch.DOCUMENT)
            .extractNextJs<DeferredPayload>()
            ?.payloadKey
            ?: throw Exception("Deferred payload key not found")

        val dataUrl = "$baseUrl/ad-frame/data".toHttpUrl().newBuilder()
            .addQueryParameter("k", payloadKey)
            .build()

        return client.get(dataUrl, Fetch.XHR.from(url)).parseAs()
    }

    /**
     * Deferred list items and detail/chapter chaotic payloads are XOR-obfuscated byte-by-byte,
     * with each byte shifted by 19968 into CJK Unified Ideographs.
     */
    private fun decodeXorStream(encoded: String, key: String): String {
        val bytes = ByteArray(encoded.length)
        for (i in encoded.indices) {
            bytes[i] = (((encoded.codePointAt(i) - 19968) xor key[i % key.length].code) and 0xFF).toByte()
        }
        return bytes.decodeToString()
    }

    // ============================== Filters ==============================

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val response = fetchDeferredData<ListResponse>("$baseUrl/manga-list.html".toHttpUrl())
        return GenresPayload(response.genresList).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<GenresPayload>()
            ?.genres
            ?.map { Genre(it.name, it.id) }
            .orEmpty()

        return FilterList(
            AuthorFilter(),
            SortFilter(),
            MatchingLogic(),
            Filter.Separator(),
            GenreList(genres),
        )
    }

    // ============================= Companion =============================

    private companion object {
        const val LIST_PAYLOAD_KEY = "NicoManga"
        const val DETAIL_PAYLOAD_KEY = "NicoMangaX2"
    }
}
