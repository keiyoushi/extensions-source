package eu.kanade.tachiyomi.extension.vi.mimi

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
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Source
abstract class MiMi : KeiSource() {
    private val apiUrl: String = "$baseUrl/api"

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(3)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegments("manga")
            .addQueryParameter("sort", "views")
            .addCommonParams(page)
            .build()

        return client.get(url).use { parseMangaPage(it) }
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegments("manga")
            .addQueryParameter("sort", "updated_at")
            .addCommonParams(page)
            .build()

        return client.get(url).use { parseMangaPage(it) }
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val isAdvanced = filters.any {
            (it is GenresFilter && it.state.any { g -> g.state != Filter.TriState.STATE_IGNORE }) ||
                (it is TextField && it.state.isNotEmpty())
        }
        val sortId = filters.filterIsInstance<SortByFilter>().firstOrNull()?.selectedSort ?: ""

        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("manga")
            when {
                isAdvanced -> addPathSegments("advanced-search")
                sortId.isEmpty() -> addPathSegments("search")
                else -> {}
            }

            filters.forEach { filter ->
                when (filter) {
                    is SortByFilter -> {
                        if (!isAdvanced && sortId.isNotEmpty()) addQueryParameter("sort", sortId)
                    }

                    is GenresFilter -> if (isAdvanced) {
                        filter.state.forEach {
                            when (it.state) {
                                Filter.TriState.STATE_INCLUDE -> addQueryParameter("genre", it.id.toString())
                                Filter.TriState.STATE_EXCLUDE -> addQueryParameter("exclude_genre", it.id.toString())
                            }
                        }
                    }

                    is TextField -> if (isAdvanced && filter.state.isNotEmpty()) {
                        if (filter.key == "author") {
                            filter.state.toIntOrNull()?.let { setQueryParameter(filter.key, it.toString()) }
                        } else {
                            setQueryParameter(filter.key, filter.state)
                        }
                    }
                    else -> {}
                }
            }
            addQueryParameter("page", page.toString())
            addQueryParameter("page_size", "24")
            if (query.isNotBlank()) addQueryParameter("title", query)
        }.build()

        return client.get(url).use { parseMangaPage(it) }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host == baseUrl.toHttpUrl().host && url.pathSegments.size >= 2 && url.pathSegments[0] == "manga") {
            val id = url.pathSegments[1]
            return client.get("$apiUrl/manga/$id").use { response ->
                response.parseAs<MangaDto>().toSManga()
            }
        }
        return null
    }

    private fun parseMangaPage(response: Response): MangasPage {
        val result = response.parseAs<DataDto>()
        val mangas = result.items.map { it.toSMangaBasic() }
        val hasNextPage = result.hasNext
        return MangasPage(mangas, hasNextPage)
    }

    private fun HttpUrl.Builder.addCommonParams(page: Int) = apply {
        addQueryParameter("exclude_genre", "196")
        addQueryParameter("page", page.toString())
        addQueryParameter("page_size", "45")
    }

    // =========================== Manga Details ============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val id = manga.url.substringAfterLast("/")

        val details = client.get("$apiUrl/manga/$id").use {
            it.parseAs<MangaDto>().toSManga()
        }

        val chaptersList = client.get("$apiUrl/manga/$id/chapters").use { response ->
            response.parseAs<List<ChapterDto>>().map { it.toSChapter(id) }
        }

        return SMangaUpdate(details, chaptersList)
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url.substringAfter("/")
        return client.get("$apiUrl/chapters/$chapterId").use { response ->
            response.parseAs<PageDto>().toPage()
        }
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/genres").use {
        it.parseAs<JsonElement>()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genreList = data?.parseAs<List<GenreDto>>()?.sortedBy { it.name } ?: emptyList()
        return FilterList(
            buildList {
                add(SortByFilter())
                add(TextField("Parody", "parody"))
                add(TextField("Nhân vật", "character"))
                add(Filter.Header("ID Tác giả (chỉ nhập số)"))
                add(TextField("ID Tác giả", "author"))

                if (genreList.isNotEmpty()) {
                    add(GenresFilter(genreList))
                }
            },
        )
    }
}
