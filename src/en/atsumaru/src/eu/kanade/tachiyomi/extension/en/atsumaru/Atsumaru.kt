package eu.kanade.tachiyomi.extension.en.atsumaru

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
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
        SortFilter(),
        AdultFilter(),
        OfficialFilter(),
    )

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/collections/manga/documents/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query.ifEmpty { "*" })

            val filterBy = mutableListOf<String>()
            filterBy.add("hidden:!=true")

            val includedGenres = mutableListOf<String>()
            val excludedGenres = mutableListOf<String>()
            val typesList = mutableListOf<String>()
            val statuses = mutableListOf<String>()
            var year: Int? = null
            var minChapters: Int? = null
            var showAdult = false
            var officialTranslation = false
            var sortBy = ""

            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        filter.state.forEachIndexed { index, state ->
                            when (state.state) {
                                Filter.TriState.STATE_INCLUDE -> includedGenres.add(filter.genreIds[index])
                                Filter.TriState.STATE_EXCLUDE -> excludedGenres.add(filter.genreIds[index])
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
                        sortBy = SortFilter.VALUES[filter.state!!.index]
                    }

                    is AdultFilter -> {
                        showAdult = filter.state
                    }

                    is OfficialFilter -> {
                        officialTranslation = filter.state
                    }

                    else -> {}
                }
            }

            if (includedGenres.isNotEmpty()) {
                filterBy.add(includedGenres.joinToString(" && ") { "genreIds:=`$it`" })
            }
            if (excludedGenres.isNotEmpty()) {
                filterBy.add("genreIds:!=[${excludedGenres.joinToString(",") { "`$it`" }}]")
            }

            if (typesList.isNotEmpty()) {
                filterBy.add("type:=[${typesList.joinToString(",") { "`$it`" }}]")
            }

            if (statuses.isNotEmpty()) {
                filterBy.add("status:=[${statuses.joinToString(",") { "`$it`" }}]")
            }

            year?.let {
                filterBy.add("releaseYear:=[$it]")
            }

            minChapters?.let {
                filterBy.add("chapterCount:>=$it")
            }

            if (!showAdult) {
                filterBy.add("isAdult:=$showAdult")
            }

            if (officialTranslation) {
                filterBy.add("officialTranslation:=$officialTranslation")
            }

            filterBy.add("(mbContentRating:=[`Safe`,`Suggestive`,`Erotica`] || mbContentRating:!=*)")
            filterBy.add("views:>0")

            addQueryParameter("filter_by", filterBy.joinToString(" && "))

            if (sortBy.isNotEmpty()) {
                addQueryParameter("sort_by", sortBy)
            }

            if (query.isNotEmpty()) {
                addQueryParameter("query_by", "title,englishTitle,otherNames,authors")
                addQueryParameter("query_by_weights", "4,3,2,1")
                addQueryParameter("num_typos", "4,3,2,1")
            }

            addQueryParameter("page", page.toString())
            addQueryParameter("per_page", "40")
        }.build()

        return GET(url, apiHeaders)
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

    override fun relatedMangaListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun relatedMangaListParse(response: Response) = response.parseAs<MangaObjectDto>().mangaPage.recommendations(baseUrl)

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl/api/manga/allChapters?mangaId=${manga.url}", apiHeaders)

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaId = response.request.url.queryParameter("mangaId")!!

        val scanlatorMap = try {
            val detailsRequest = mangaDetailsRequest(SManga.create().apply { url = mangaId })
            client.newCall(detailsRequest).execute().use { response ->
                response.parseAs<MangaObjectDto>().mangaPage.scanlators?.associate { it.id to it.name }
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
        Page(index, imageUrl = imageUrl.replaceFirst(PROTOCOL_REGEX, "https://"))
    }

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder().apply {
            add("Accept", "image/avif,image/webp,*/*")
            add("Referer", baseUrl)
        }.build()

        return GET(page.imageUrl!!, imgHeaders)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val PROTOCOL_REGEX = Regex("^https?:?//")
    }
}
