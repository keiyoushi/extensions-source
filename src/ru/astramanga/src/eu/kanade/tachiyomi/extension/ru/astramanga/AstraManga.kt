package eu.kanade.tachiyomi.extension.ru.astramanga

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
import keiyoushi.utils.string
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.collections.forEach
import kotlin.math.ceil

@Source
abstract class AstraManga : KeiSource() {

    private val domain get() = baseUrl.toHttpUrl().host
    private val apiUrl get() = "https://api.$domain/api/v1"
    private val mediaUrl get() = "https://$domain/media"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(2)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = makeCatalogRequest("-popularity", page)

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = makeCatalogRequest("-updated_at", page)

    // ============================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = makeCatalogRequest("-popularity", page, query, filters)

    private suspend fun makeCatalogRequest(sortBy: String, page: Int, query: String? = null, filters: FilterList? = null): MangasPage {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("search")

            filters?.forEach { filter ->
                when (filter) {
                    is TypeFilter -> filter.selected.takeIf { it.isNotEmpty() }
                        ?.let { addQueryParameter("type", it) }

                    is StatusFilter -> filter.selected.takeIf { it.isNotEmpty() }
                        ?.let { addQueryParameter("status", it) }

                    is SortFilter -> addQueryParameter("sort", filter.selected)
                    is GenreFilter -> {
                        filter.included?.forEach { addQueryParameter("genres", it) }
                        filter.excluded?.forEach { addQueryParameter("exclude_genres", it) }
                    }
                    is TagsFilter -> {
                        filter.included?.forEach { addQueryParameter("tags", it) }
                        filter.excluded?.forEach { addQueryParameter("exclude_tags", it) }
                    }

                    else -> {}
                }
            }

            if (filters == null) addQueryParameter("sort", sortBy)
            if (query?.isNotBlank() == true) {
                addQueryParameter("query", query.trim())
            }
            addQueryParameter("page", page.toString())
            addQueryParameter("page_size", PAGE_SIZE.toString())
        }.build()

        return client.get(url).use { response ->
            val data = response.parseAs<SearchResponse>().data
            val mangas = data.titles.map { it.toSManga(mediaUrl) }
            MangasPage(mangas, data.currentPage < data.totalPages)
        }
    }

    // =========================== Deeplink ============================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host == domain && url.pathSegments[0] == "manga" && url.pathSegments[1].length > 1) {
            val tmpManga = SManga.create().apply {
                this.url = url.pathSegments[1]
            }

            return fetchMangaUpdate(tmpManga, emptyList(), fetchDetails = true, fetchChapters = false).manga
        }

        return null
    }

    // ============================== Details ===============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url

        val mangaNew = if (fetchDetails || manga.memo["id"]?.string.isNullOrBlank()) {
            client.get("$apiUrl/titles/$slug").parseAs<TitleDetailResponse>().data.toSMangaDetails(mediaUrl)
        } else {
            manga
        }

        val chaptersNew = if (fetchChapters) {
            val titleId = mangaNew.memo["id"]!!.string
            val branches = client.get("$apiUrl/titles/$titleId/branches")
                .parseAs<BranchesResponse>().data.branches

            coroutineScope {
                branches.map { branch ->
                    async {
                        val pageSize = branch.countChapters?.takeIf { it > 0 } ?: 0
                        val totalPages = ceil(pageSize.toDouble() / CHAPTERS_PAGE_SIZE).toInt().coerceAtLeast(1)

                        (1..totalPages).map { page ->
                            async {
                                val url = "$apiUrl/branches/${branch.id}/chapters?page=$page&page_size=$CHAPTERS_PAGE_SIZE"
                                client.get(url).parseAs<ChaptersResponse>().data.items
                            }
                        }.awaitAll().flatten().map { it.toSChapter(slug, branch.name) }
                    }
                }.awaitAll().flatten().sortedWith(
                    compareByDescending<SChapter> { it.chapter_number }.thenByDescending { it.date_upload },
                )
            }
        } else {
            chapters
        }

        return SMangaUpdate(mangaNew, chaptersNew)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    // ============================== Chapters ==============================

    override fun getChapterUrl(chapter: SChapter): String {
        val (slug, number, id) = chapter.url.split("/", limit = 3)
        return "$baseUrl/manga/$slug/read/$number?chapterId=$id"
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val pages = client.get("$apiUrl/chapters/${chapter.url.substringAfterLast('/')}/pages")
            .parseAs<PagesResponse>().data.pages
        // page_number is non-sequential (sliced webtoon images); rely on array order.
        return pages.mapIndexed { index, p -> Page(index, imageUrl = p.imageUrl) }
    }

    // ============================== Filters ===============================

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(),
        GenreFilter(),
        TagsFilter(),
        TypeFilter(),
        StatusFilter(),
    )

    companion object {
        private const val PAGE_SIZE = 30
        private const val CHAPTERS_PAGE_SIZE = 5000
    }
}
