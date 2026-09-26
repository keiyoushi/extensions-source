package eu.kanade.tachiyomi.multisrc.uzaymanga

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

abstract class UzayManga : KeiSource() {

    protected open val cdnUrl: String? = null

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/manga/__data.json".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "popular")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("x-sveltekit-invalidated", "001")
            .build()
        val response = client.get(url, headers)
        return searchMangaParse(response)
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/manga/__data.json".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "update")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("x-sveltekit-invalidated", "001")
            .build()
        val response = client.get(url, headers)
        return searchMangaParse(response)
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.startsWith(URL_SEARCH_PREFIX)) {
            val slug = query.substringAfter(URL_SEARCH_PREFIX)
            val url = "$baseUrl/manga/$slug/__data.json".toHttpUrl().newBuilder()
                .addQueryParameter("x-sveltekit-invalidated", "001")
                .build()

            val response = client.get(url, headers)
            val manga = mangaDetailsParse(response).apply { this.url = "/manga/$slug" }
            return MangasPage(listOf(manga), false)
        }

        val url = "$baseUrl/manga/__data.json".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("x-sveltekit-invalidated", "001")

        if (query.isNotBlank()) {
            url.addQueryParameter("search", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is CategoryFilter -> if (filter.state != 0) url.addQueryParameter("category", filter.toUriPart())
                is StatusFilter -> if (filter.state != 0) url.addQueryParameter("status", filter.toUriPart())
                is CountryFilter -> if (filter.state != 0) url.addQueryParameter("country", filter.toUriPart())
                is SortFilter -> url.addQueryParameter("sort", filter.toUriPart())
                else -> {}
            }
        }

        if (filters.none { it is SortFilter } && query.isBlank()) {
            url.addQueryParameter("sort", "new")
        }

        val response = client.get(url.build(), headers)
        return searchMangaParse(response)
    }

    private fun searchMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<SvelteResponse>()
        val dataArray = dto.getData() ?: return MangasPage(emptyList(), false)
        val svelte = SvelteData(dataArray)

        val root = svelte.getObject(0) ?: return MangasPage(emptyList(), false)

        val seriesIdx = root["series"]?.jsonPrimitive?.intOrNull ?: return MangasPage(emptyList(), false)
        val seriesArray = svelte.getArray(seriesIdx) ?: return MangasPage(emptyList(), false)

        val mangas = seriesArray.mapNotNull {
            val mangaIdx = it.jsonPrimitive.intOrNull ?: return@mapNotNull null
            val mangaObj = svelte.getObject(mangaIdx) ?: return@mapNotNull null

            SManga.create().apply {
                title = svelte.resolveString(mangaObj, "name") ?: return@mapNotNull null
                val imagePath = svelte.resolveString(mangaObj, "image") ?: ""
                val baseImgUrl = cdnUrl?.removeSuffix("/") ?: baseUrl.removeSuffix("/")
                thumbnail_url = if (imagePath.startsWith("http")) imagePath else "$baseImgUrl/${imagePath.removePrefix("/")}"
                val slug = svelte.resolveString(mangaObj, "slug") ?: return@mapNotNull null
                url = "/manga/$slug"
            }
        }

        val currentPage = svelte.resolveInt(root, "currentPage") ?: 1
        val totalPages = svelte.resolveInt(root, "totalPages") ?: 1

        return MangasPage(mangas, currentPage < totalPages)
    }

    // ============================== Details ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val pathSegments = url.pathSegments.filter { it.isNotEmpty() }
        if (pathSegments.size < 2 || pathSegments[0] != "manga") return null
        val slug = pathSegments[1]
        val reqUrl = "$baseUrl/manga/$slug/__data.json".toHttpUrl().newBuilder()
            .addQueryParameter("x-sveltekit-invalidated", "001")
            .build()
        val response = client.get(reqUrl, headers)
        return mangaDetailsParse(response).apply { this.url = "/manga/$slug" }.takeIf { it.title.isNotEmpty() }
    }

    private fun mangaDetailsParse(response: Response): SManga {
        val dto = response.parseAs<SvelteResponse>()
        val dataArray = dto.getData() ?: return SManga.create()
        val svelte = SvelteData(dataArray)
        val root = svelte.getObject(0) ?: return SManga.create()

        val seriesIdx = root["series"]?.jsonPrimitive?.intOrNull ?: return SManga.create()
        val seriesObj = svelte.getObject(seriesIdx) ?: return SManga.create()

        return parseMangaDetails(seriesObj, svelte)
    }

    private fun parseMangaDetails(seriesObj: JsonObject, svelte: SvelteData): SManga = SManga.create().apply {
        title = svelte.resolveString(seriesObj, "name") ?: ""
        val imagePath = svelte.resolveString(seriesObj, "image") ?: ""
        val baseImgUrl = cdnUrl?.removeSuffix("/") ?: baseUrl.removeSuffix("/")
        thumbnail_url = if (imagePath.startsWith("http")) imagePath else "$baseImgUrl/${imagePath.removePrefix("/")}"
        description = svelte.resolveString(seriesObj, "description")

        status = when (svelte.resolveInt(seriesObj, "status")) {
            1 -> SManga.ONGOING
            2 -> SManga.COMPLETED
            3 -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }

        val resolvedCatArray = svelte.resolveArray(seriesObj, "resolvedCategories")
        if (resolvedCatArray != null) {
            genre = resolvedCatArray.mapNotNull {
                val catObjIdx = it.jsonPrimitive.intOrNull ?: return@mapNotNull null
                val catObj = svelte.getObject(catObjIdx) ?: return@mapNotNull null
                svelte.resolveString(catObj, "title")
            }.joinToString()
        }
    }

    // ============================= Chapters ==============================

    private fun parseChapterList(seriesObj: JsonObject, svelte: SvelteData): List<SChapter> {
        val seriesSlug = svelte.resolveString(seriesObj, "slug") ?: return emptyList()
        val chaptersArray = svelte.resolveArray(seriesObj, "SeriesEpisode") ?: return emptyList()

        return chaptersArray.mapNotNull {
            val chapIdx = it.jsonPrimitive.intOrNull ?: return@mapNotNull null
            val chapObj = svelte.getObject(chapIdx) ?: return@mapNotNull null

            SChapter.create().apply {
                val chapName = svelte.resolveString(chapObj, "name")
                val chapOrder = svelte.resolveString(chapObj, "order")?.removeSuffix(".0")
                name = buildString {
                    if (chapOrder != null) append("Bölüm $chapOrder")
                    if (chapName != null && chapName != chapOrder) {
                        if (isNotEmpty()) append(" - ")
                        append(chapName)
                    }
                    if (isEmpty()) append("Bölüm")
                }

                val slug = svelte.resolveString(chapObj, "slug") ?: return@mapNotNull null
                url = "/manga/$seriesSlug/$slug"

                date_upload = svelte.resolveDate(chapObj, "createdDate")
            }
        }
    }

    // ========================== Update Strategy ==========================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = "$baseUrl${manga.url}/__data.json".toHttpUrl().newBuilder()
            .addQueryParameter("x-sveltekit-invalidated", "001")
            .build()
        val response = client.get(url, headers)
        val dto = response.parseAs<SvelteResponse>()
        val dataArray = dto.getData() ?: return SMangaUpdate(manga, chapters)
        val svelte = SvelteData(dataArray)
        val root = svelte.getObject(0) ?: return SMangaUpdate(manga, chapters)

        val seriesIdx = root["series"]?.jsonPrimitive?.intOrNull ?: return SMangaUpdate(manga, chapters)
        val seriesObj = svelte.getObject(seriesIdx) ?: return SMangaUpdate(manga, chapters)

        val updatedManga = parseMangaDetails(seriesObj, svelte).apply { this.url = manga.url }
        val updatedChapters = parseChapterList(seriesObj, svelte)
        return SMangaUpdate(updatedManga, updatedChapters)
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$baseUrl${chapter.url}/__data.json".toHttpUrl().newBuilder()
            .addQueryParameter("x-sveltekit-invalidated", "001")
            .build()
        val response = client.get(url, headers)

        val dto = response.parseAs<SvelteResponse>()
        val dataArray = dto.getData() ?: return emptyList()
        val svelte = SvelteData(dataArray)

        val root = svelte.getObject(0) ?: return emptyList()
        val episodeIdx = root["episode"]?.jsonPrimitive?.intOrNull ?: return emptyList()
        val episodeObj = svelte.getObject(episodeIdx) ?: return emptyList()

        val imagesArray = svelte.resolveArray(episodeObj, "images") ?: return emptyList()

        return imagesArray.mapIndexedNotNull { index, element ->
            val imageIdx = element.jsonPrimitive.intOrNull ?: return@mapIndexedNotNull null
            val imagePath = svelte.getString(imageIdx) ?: return@mapIndexedNotNull null

            val baseImgUrl = cdnUrl?.removeSuffix("/") ?: baseUrl.removeSuffix("/")
            val imageUrl = if (imagePath.startsWith("http")) imagePath else "$baseImgUrl/${imagePath.removePrefix("/")}"
            Page(index, imageUrl = imageUrl)
        }
    }

    // ============================== Filters ==============================

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(),
        CategoryFilter(),
        StatusFilter(),
        CountryFilter(),
    )

    companion object {
        const val URL_SEARCH_PREFIX = "slug:"
    }
}
