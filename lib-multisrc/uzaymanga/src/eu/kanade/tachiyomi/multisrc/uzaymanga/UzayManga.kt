package eu.kanade.tachiyomi.multisrc.uzaymanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable

abstract class UzayManga(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    override val versionId: Int,
    private val cdnUrl: String? = null,
) : HttpSource() {

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/manga/__data.json".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "popular")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("x-sveltekit-invalidated", "001")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/manga/__data.json".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "new")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("x-sveltekit-invalidated", "001")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // ============================== Search ===============================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(URL_SEARCH_PREFIX)) {
            val slug = query.substringAfter(URL_SEARCH_PREFIX)
            val url = "$baseUrl/manga/$slug/__data.json".toHttpUrl().newBuilder()
                .addQueryParameter("x-sveltekit-invalidated", "001")
                .build()

            return client.newCall(GET(url, headers)).asObservableSuccess().map { response ->
                val manga = mangaDetailsParse(response)
                manga.url = "/manga/$slug"
                MangasPage(listOf(manga), false)
            }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
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

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
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

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = "$baseUrl${manga.url}/__data.json".toHttpUrl().newBuilder()
            .addQueryParameter("x-sveltekit-invalidated", "001")
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.parseAs<SvelteResponse>()
        val dataArray = dto.getData() ?: return SManga.create()
        val svelte = SvelteData(dataArray)
        val root = svelte.getObject(0) ?: return SManga.create()

        val seriesIdx = root["series"]?.jsonPrimitive?.intOrNull ?: return SManga.create()
        val seriesObj = svelte.getObject(seriesIdx) ?: return SManga.create()

        return SManga.create().apply {
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
    }

    // ============================= Chapters ==============================

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val dto = response.parseAs<SvelteResponse>()
        val dataArray = dto.getData() ?: return emptyList()
        val svelte = SvelteData(dataArray)
        val root = svelte.getObject(0) ?: return emptyList()

        val seriesIdx = root["series"]?.jsonPrimitive?.intOrNull ?: return emptyList()
        val seriesObj = svelte.getObject(seriesIdx) ?: return emptyList()

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

    // =============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request {
        val url = "$baseUrl${chapter.url}/__data.json".toHttpUrl().newBuilder()
            .addQueryParameter("x-sveltekit-invalidated", "001")
            .build()
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
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

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList() = FilterList(
        SortFilter(),
        CategoryFilter(),
        StatusFilter(),
        CountryFilter(),
    )

    companion object {
        const val URL_SEARCH_PREFIX = "slug:"
    }
}
