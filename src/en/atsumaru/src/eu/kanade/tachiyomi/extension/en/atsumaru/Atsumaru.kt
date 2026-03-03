package eu.kanade.tachiyomi.extension.en.atsumaru

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class Atsumaru : HttpSource() {

    override val versionId = 2

    override val name = "Atsumaru"

    override val baseUrl = "https://atsu.moe"

    override val lang = "en"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    private fun apiHeadersBuilder() = headersBuilder().apply {
        add("Accept", "*/*")
        add("Referer", baseUrl)
        add("Content-Type", "application/json")
    }

    private val apiHeaders by lazy { apiHeadersBuilder().build() }

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/infinite/trending?page=${page - 1}&types=Manga,Manwha,Manhua,OEL", apiHeaders)

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<BrowseMangaDto>().items

        return MangasPage(data.map { it.toSManga(baseUrl) }, true)
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/api/infinite/recentlyUpdated?page=${page - 1}&types=Manga,Manwha,Manhua,OEL", apiHeaders)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // =============================== Search ===============================

    override fun getFilterList() = FilterList(
        Filter.Separator(),
        GenreFilter(getGenresList()),
        TypeFilter(getTypesList()),
        StatusFilter(getStatusList()),
        YearFilter(),
        MinChaptersFilter(),
        Filter.Separator(),
        SortFilter(),
    )

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val jsonPayload = buildSearchRequest(page - 1, filters, query)
        val jsonString = Json.encodeToString(
            SearchRequest.serializer(),
            jsonPayload,
        )
        val requestBody = jsonString.toRequestBody("application/json".toMediaType())

        return POST("$baseUrl/api/explore/filteredView", apiHeaders, requestBody)
    }

    private fun buildSearchRequest(page: Int, filters: FilterList, query: String): SearchRequest {
        val selectedGenres = mutableListOf<String>()
        val typesList = mutableListOf<String>()
        val statuses = mutableListOf<String>()
        var year: Int? = null
        var minChapters: Int? = null
        var sort = "popularity"

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    filter.state.forEachIndexed { index, checkBox ->
                        if (checkBox.state) {
                            selectedGenres.add(filter.genreIds[index])
                        }
                    }
                }

                is TypeFilter -> {
                    filter.state.forEachIndexed { index, checkBox ->
                        if (checkBox.state) {
                            typesList.add(filter.ids[index])
                        }
                    }
                }

                is StatusFilter -> {
                    filter.state.forEachIndexed { index, checkBox ->
                        if (checkBox.state) {
                            statuses.add(filter.ids[index])
                        }
                    }
                }

                is YearFilter -> {
                    if (filter.state.isNotEmpty()) year = filter.state.toIntOrNull()
                }

                is MinChaptersFilter -> {
                    if (filter.state.isNotEmpty()) minChapters = filter.state.toIntOrNull()
                }

                is SortFilter -> {
                    sort = SortFilter.VALUES[filter.state!!.index]
                }

                else -> {}
            }
        }

        val types = typesList.ifEmpty {
            listOf("Manga", "Manwha", "Manhua", "OEL")
        }

        return SearchRequest(
            page = page,
            sort = sort,
            filter = SearchFilter(
                search = query.ifEmpty { null },
                types = types,
                status = statuses.ifEmpty { null },
                includedTags = selectedGenres.ifEmpty { null },
                year = year,
                minChapters = minChapters,
            ),
        )
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val body = response.body.string()

        return if (body.contains("\"hits\"")) {
            val data = body.parseAs<SearchResultsDto>()
            MangasPage(data.hits.map { it.document.toSManga(baseUrl) }, data.hasNextPage())
        } else {
            val data = body.parseAs<BrowseMangaDto>()
            MangasPage(data.items.map { it.toSManga(baseUrl) }, true)
        }
    }

    // =========================== Manga Details ============================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/api/manga/page?id=${manga.url}", apiHeaders)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<MangaObjectDto>().mangaPage.toSManga(baseUrl)

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl/api/manga/allChapters?mangaId=${manga.url}", apiHeaders)

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaId = response.request.url.queryParameter("mangaId")!!

        val scanlatorMap = try {
            val detailsRequest = mangaDetailsRequest(SManga.create().apply { url = mangaId })
            client.newCall(detailsRequest).execute().use {
                it.parseAs<MangaObjectDto>().mangaPage.scanlators?.associate { it.id to it.name }
            }.orEmpty()
        } catch (_: Exception) {
            emptyMap()
        }

        val data = response.parseAs<AllChaptersDto>()

        return data.chapters.map {
            it.toSChapter(mangaId, it.scanlationMangaId?.let { id -> scanlatorMap[id] })
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val (slug, name) = chapter.url.split("/")
        return "$baseUrl/read/$slug/$name"
    }

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request {
        val (slug, name) = chapter.url.split("/")
        val url = "$baseUrl/api/read/chapter".toHttpUrl().newBuilder()
            .addQueryParameter("mangaId", slug)
            .addQueryParameter("chapterId", name)

        return GET(url.build(), apiHeaders)
    }

    override fun pageListParse(response: Response): List<Page> = response.parseAs<PageObjectDto>().readChapter.pages.mapIndexed { index, page ->
        val imageUrl = when {
            page.image.startsWith("http") -> page.image
            page.image.startsWith("//") -> "https:${page.image}"
            else -> "$baseUrl/static/${page.image.removePrefix("/").removePrefix("static/")}"
        }
        Page(index, imageUrl = imageUrl.replaceFirst(Regex("^https?:?//"), "https://"))
    }

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder().apply {
            add("Accept", "image/avif,image/webp,*/*")
            add("Referer", baseUrl)
        }.build()

        return GET(page.imageUrl!!, imgHeaders)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
