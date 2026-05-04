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
            addQueryParameter("query_by", "title,englishTitle,otherNames,authors")
            addQueryParameter("query_by_weights", "4,3,2,1")
            addQueryParameter("num_typos", "4,3,2,1")
            addQueryParameter("include_fields", "id,title,englishTitle,poster,posterSmall,posterMedium,type,isAdult,status,year,synopsis,otherNames,mbRating,avgRating,authors")
            addQueryParameter("page", page.toString())
            addQueryParameter("per_page", "40")

            val filterBy = mutableListOf<String>()
            filterBy.add("hidden:!=true")

            var showAdult = false
            filters.forEach { if (it is AdultFilter) showAdult = it.state }

            if (!showAdult) {
                filterBy.add("isAdult:=false")
                filterBy.add("(mbContentRating:=[`Safe`,`Suggestive`,`Erotica`] || mbContentRating:!=*)")
            }

            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        val included = mutableListOf<String>()
                        val excluded = mutableListOf<String>()
                        filter.state.forEachIndexed { index, state ->
                            val genreId = filter.genreIds[index]
                            when (state.state) {
                                Filter.TriState.STATE_INCLUDE -> included.add(genreId)
                                Filter.TriState.STATE_EXCLUDE -> excluded.add(genreId)
                            }
                        }
                        included.forEach { filterBy.add("genreIds:=`$it`") }
                        if (excluded.isNotEmpty()) {
                            filterBy.add("genreIds:!=[${excluded.joinToString(",") { "`$it`" }}]")
                        }
                    }

                    is TypeFilter -> {
                        val types = filter.state.filter { it.state }.map { filter.ids[filter.state.indexOf(it)] }
                        if (types.isNotEmpty()) {
                            filterBy.add("type:=[${types.joinToString(",") { "`$it`" }}]")
                        }
                    }

                    is StatusFilter -> {
                        val statuses = filter.state.filter { it.state }.map { filter.ids[filter.state.indexOf(it)] }
                        if (statuses.isNotEmpty()) {
                            filterBy.add("status:=[${statuses.joinToString(",") { "`$it`" }}]")
                        }
                    }

                    is YearFilter -> {
                        if (filter.state.isNotEmpty()) {
                            val years = filter.state.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            if (years.isNotEmpty()) {
                                filterBy.add("releaseYear:=[${years.joinToString(",")}]")
                            }
                        }
                    }

                    is MinChaptersFilter -> {
                        if (filter.state.isNotEmpty()) {
                            filterBy.add("chapterCount:>=${filter.state}")
                        }
                    }

                    is SortFilter -> {
                        addQueryParameter("sort_by", filter.getSortBy())
                    }

                    is OfficialFilter -> {
                        if (filter.state) {
                            filterBy.add("officialTranslation:=true")
                        }
                    }

                    else -> {}
                }
            }

            filterBy.add("views:>0")

            addQueryParameter("filter_by", filterBy.joinToString(" && "))
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
