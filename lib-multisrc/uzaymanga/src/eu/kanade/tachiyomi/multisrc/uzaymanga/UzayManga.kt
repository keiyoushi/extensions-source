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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

// The listing endpoint returns bogus currentPage/totalPages values, so the last page is
// detected by a short page instead.
private const val PAGE_SIZE = 20

abstract class UzayManga : KeiSource() {

    protected open val cdnUrl: String? = null

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter().apply { state = 1 }))

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/__data.json".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        val (svelte, root) = client.get(url).parseSvelteRoot() ?: return MangasPage(emptyList(), false)

        val lastEpisodes = svelte.resolveObject(root, "lastEpisodes") ?: return MangasPage(emptyList(), false)
        val seriesArray = svelte.resolveArray(lastEpisodes, "data") ?: return MangasPage(emptyList(), false)

        val currentPage = svelte.resolveInt(lastEpisodes, "currentPage") ?: 1
        val totalPages = svelte.resolveInt(lastEpisodes, "totalPage") ?: 1
        return MangasPage(svelte.toSMangaList(seriesArray, baseUrl, cdnUrl), currentPage < totalPages)
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
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

        val (svelte, root) = client.get(url.build()).parseSvelteRoot() ?: return MangasPage(emptyList(), false)
        val seriesArray = svelte.resolveArray(root, "series") ?: return MangasPage(emptyList(), false)

        return MangasPage(svelte.toSMangaList(seriesArray, baseUrl, cdnUrl), seriesArray.size == PAGE_SIZE)
    }

    // ============================== Details ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val pathSegments = url.pathSegments.filter { it.isNotEmpty() }
        if (pathSegments.size < 2 || pathSegments[0] != "manga") return null

        val manga = SManga.create().apply { this.url = "/manga/${pathSegments[1]}" }

        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .takeIf { it.title.isNotEmpty() }
    }

    private fun parseMangaDetails(seriesObj: JsonObject, svelte: SvelteData): SManga? {
        val manga = svelte.toSManga(seriesObj, baseUrl, cdnUrl) ?: return null

        return manga.apply {
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
        val (svelte, root) = client.get(url).parseSvelteRoot() ?: return SMangaUpdate(manga, chapters)
        val seriesObj = svelte.resolveObject(root, "series") ?: return SMangaUpdate(manga, chapters)

        val updatedManga = parseMangaDetails(seriesObj, svelte)?.apply { this.url = manga.url } ?: manga
        val updatedChapters = parseChapterList(seriesObj, svelte)
        return SMangaUpdate(updatedManga, updatedChapters)
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$baseUrl${chapter.url}/__data.json".toHttpUrl().newBuilder()
            .addQueryParameter("x-sveltekit-invalidated", "001")
            .build()
        val (svelte, root) = client.get(url).parseSvelteRoot() ?: return emptyList()
        val episodeObj = svelte.resolveObject(root, "episode") ?: return emptyList()
        val imagesArray = svelte.resolveArray(episodeObj, "images") ?: return emptyList()

        return imagesArray.mapIndexedNotNull { index, element ->
            val imageIdx = element.jsonPrimitive.intOrNull ?: return@mapIndexedNotNull null
            val imagePath = svelte.getString(imageIdx) ?: return@mapIndexedNotNull null

            Page(index, imageUrl = resolveImageUrl(imagePath, baseUrl, cdnUrl))
        }
    }

    // ============================== Filters ==============================

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(),
        CategoryFilter(),
        StatusFilter(),
        CountryFilter(),
    )
}
