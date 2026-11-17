package eu.kanade.tachiyomi.extension.ko.ridibooks

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.graphQLBody
import keiyoushi.utils.parseAs
import keiyoushi.utils.parseGraphQLAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.UUID

@Source
abstract class Ridibooks : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getCategoryBooks(page, CategoryFilter().toUriPart(), "popular")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getCategoryBooks(page, CategoryFilter().toUriPart(), "recent")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val category = (filters.firstInstanceOrNull<CategoryFilter>() ?: CategoryFilter()).toUriPart()
        if (query.isBlank()) {
            return getCategoryBooks(page, category, (filters.firstInstanceOrNull<SortFilter>() ?: SortFilter()).toUriPart())
        }

        val start = (page - 1) * PAGE_SIZE
        val url = "$SEARCH_API_URL/search".toHttpUrl().newBuilder()
            .addQueryParameter("site", "ridi-store")
            .addQueryParameter("where", "book")
            .addQueryParameter("what", "base")
            .addQueryParameter("keyword", query.trim())
            .addQueryParameter("category_id", category)
            .addQueryParameter("start", start.toString())
            .addQueryParameter("size", PAGE_SIZE.toString())
            .build()
        val result = client.get(url, Fetch.XHR).parseAs<SearchDto>()
        return MangasPage(
            result.books.map { it.toSManga() },
            start + result.books.size < result.total,
        )
    }

    private suspend fun getCategoryBooks(page: Int, category: String, order: String): MangasPage {
        val url = "$API_URL/v2/category/books".toHttpUrl().newBuilder()
            .addQueryParameter("platform", "web")
            .addQueryParameter("tab", "books")
            .addQueryParameter("category_id", category)
            .addQueryParameter("order_by", order)
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("offset", ((page - 1) * PAGE_SIZE).toString())
            .build()
        val data = client.get(url, Fetch.XHR).parseAs<ApiResult<CategoryBooksDto>>().data!!
        return MangasPage(
            data.items.map { it.toSManga() }.distinctBy { it.url },
            data.pagination.nextPage != null,
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.getOrNull(0) != "books") return null
        val id = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        return getBook(id).toSManga(null)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/books/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/books/${chapter.url}/view"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { if (fetchDetails) getMangaDetails(manga) else manga }
        val chapterList = async { if (fetchChapters) getChapterList(manga) else chapters }
        SMangaUpdate(details.await(), chapterList.await())
    }

    private suspend fun getBook(id: String): BookDto = client.get("$BOOK_API_URL/books/$id", Fetch.XHR).parseAs()

    private suspend fun getMangaDetails(manga: SManga): SManga = coroutineScope {
        val book = async { getBook(manga.url) }
        val intro = async {
            client.get("$BOOK_API_URL/books/${manga.url}/descriptions", Fetch.XHR)
                .parseAs<DescriptionsWrapperDto>().intro
        }
        book.await().toSManga(intro.await()).apply { url = manga.url }
    }

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        // The site sends a random UUID per page load as the cell id.
        val cellId = UUID.randomUUID().toString()
        val chapters = mutableListOf<SChapter>()
        var offset: Int? = 0
        while (offset != null) {
            val variables = EpisodeVariables(
                id = cellId,
                context = EpisodeContext(
                    bookId = manga.url,
                    pagination = EpisodePagination(offset, EPISODE_PAGE_SIZE),
                    deviceType = "DESKTOP",
                    order = "LATEST",
                    tabType = "BUY",
                ),
            )
            val cell = client.post(
                "$baseUrl/graphql",
                Fetch.XHR,
                graphQLBody(EPISODES_QUERY, "BooksDetailEpisodeBooks", variables),
            ).parseGraphQLAs<EpisodeResponseDto>().cell
                ?: break
            cell.books.mapTo(chapters) { it.toSChapter(manga.title) }
            offset = cell.pagination?.nextPage?.offset
        }

        if (chapters.isEmpty()) {
            // Standalone books have no episode list, the book itself is the only chapter.
            return listOf(
                SChapter.create().apply {
                    url = manga.url
                    name = manga.title
                },
            )
        }
        return chapters
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        var result = generateViewer(chapter)
        if (result.error?.code == "NOT_AUTHORIZED" && refreshToken()) {
            result = generateViewer(chapter)
        }

        if (!result.success) {
            when (result.error?.code) {
                "INVALID_ACCESS" -> return emptyList()
                "NOT_AUTHORIZED" -> throw Exception("Log in via WebView to read this chapter")
                "ADULT_VERIFICATION_REQUIRED" -> throw Exception("Verify your age on RidiBooks in WebView to read this chapter")
                else -> throw Exception(result.message ?: result.error?.code)
            }
        }
        return result.data!!.pages.mapIndexed { index, page -> Page(index, imageUrl = page.src) }
    }

    private suspend fun generateViewer(chapter: SChapter): ApiResult<ViewerDto> = client.post(
        "$baseUrl/api/web-viewer/generate",
        Fetch.XHR.from("/books/${chapter.url}"),
        ViewerRequest(chapter.url).toJsonRequestBody(),
        ensureSuccess = false,
    ).parseAs()

    private suspend fun refreshToken(): Boolean = client.post(
        "$baseUrl/apps/books/auth/token",
        Fetch.XHR,
        TokenRequest("refresh_token").toJsonRequestBody(),
        ensureSuccess = false,
    ).use { it.isSuccessful }

    override fun getFilterList(data: JsonElement?) = FilterList(
        CategoryFilter(),
        SortFilter(),
    )

    companion object {
        private const val API_URL = "https://api.ridibooks.com"
        private const val BOOK_API_URL = "https://book-api.ridibooks.com"
        private const val SEARCH_API_URL = "https://search-api.ridibooks.com"
        private const val PAGE_SIZE = 60
        private const val EPISODE_PAGE_SIZE = 100

        private val EPISODES_QUERY = $$"""
            query BooksDetailEpisodeBooks($id: UUID!, $context: BookDetailHomeEpisodeBookListCellContext!) {
              riGrid {
                cells {
                  bookDetailHome {
                    episodeBookListCell(id: $id, context: $context) {
                      cell {
                        ... on BookDetailHomeEpisodeBookList {
                          books {
                            bookId
                            title
                            metadata {
                              regDate
                              ownership {
                                status
                              }
                            }
                            price {
                              sellingPrice
                            }
                          }
                          pagination {
                            nextPage {
                              offset
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()
    }
}
