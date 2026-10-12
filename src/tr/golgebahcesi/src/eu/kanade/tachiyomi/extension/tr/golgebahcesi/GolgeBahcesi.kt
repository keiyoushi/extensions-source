package eu.kanade.tachiyomi.extension.tr.golgebahcesi
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Instant

@Source
abstract class GolgeBahcesi : KeiSource() {

    private val apiBaseUrl get() = "https://api.golgebahcesi.com/api"

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", SortFilter.POPULAR)

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", SortFilter.LATEST)

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = url.pathSegments.getOrNull(1) ?: return null
        return fetchMangaUpdate(
            SManga.create().apply { this.url = slug },
            emptyList(),
            true,
            false,
        ).manga
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiBaseUrl/series".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "24")
            if (query.isNotBlank()) addQueryParameter("search", query)

            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> addQueryParameter("sort", filter.toUriPart())
                    is StatusFilter -> filter.toUriPart().takeIf { it.isNotBlank() }?.let { addQueryParameter("status", it) }
                    is TypeFilter -> filter.toUriPart().takeIf { it.isNotBlank() }?.let { addQueryParameter("type", it) }
                    is GenreFilter -> filter.toUriPart().takeIf { it.isNotBlank() }?.let { addQueryParameter("genre", it) }
                    is MinChaptersFilter -> filter.state.takeIf { it.isNotBlank() }?.let { addQueryParameter("minChapters", it) }
                    else -> {}
                }
            }
        }.build()

        val result = client.get(url).parseAs<SeriesListResponse>()
        val mangas = result.data.map { it.toSManga() }
        val hasNextPage = result.pagination?.let { it.currentPage < it.totalPages } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga) = client.get(
        "$apiBaseUrl/recommendations/series/${manga.url}/similar",
    ).parseAs<Recommended>().recommendations.map { it.toSManga() }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val updatedManga = async {
            if (fetchDetails) {
                client.get("$apiBaseUrl/series/${manga.url}").parseAs<SeriesDto>().toSManga()
            } else {
                manga
            }
        }
        val updatedChapters = async {
            if (fetchChapters) {
                client.get("$apiBaseUrl/series/${manga.url}/chapters").parseAs<List<ChapterDto>>().map { chapter ->
                    SChapter.create().apply {
                        url = "/${chapter.id}/${chapter.seriesSlug}/${chapter.slug}"
                        name = chapter.title
                        chapter_number = chapter.number
                        date_upload = Instant.tryParse(chapter.releaseDate ?: chapter.createdAt)
                    }
                }
            } else {
                chapters
            }
        }

        SMangaUpdate(updatedManga.await(), updatedChapters.await())
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val segments = "$baseUrl${chapter.url}".toHttpUrl().pathSegments
        return "$baseUrl/manga/${segments[1]}/bolum/${segments[2]}"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = "$baseUrl${chapter.url}".toHttpUrl().pathSegments[0]
        return client.get(
            "$apiBaseUrl/chapters/$chapterId",
        ).parseAs<ChapterDto>().pages?.map { page ->
            Page(page.index, imageUrl = page.url)
        } ?: emptyList()
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData() = client.get("$apiBaseUrl/genres")
        .parseAs<List<Genre>>()
        .toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        listOf(
            SortFilter(),
            StatusFilter(),
            TypeFilter(),
            MinChaptersFilter(),
        ) + listOfNotNull(
            data?.parseAs<List<Genre>>()?.map { it.name to it.slug }
                ?.takeIf { it.isNotEmpty() }
                ?.let(::GenreFilter),
        ),
    )
}
