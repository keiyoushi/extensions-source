package eu.kanade.tachiyomi.extension.es.ichijou

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.IOException

@Source
abstract class Ichijou : KeiSource() {
    private val apiUrl = "https://api.ichijoutranslations.com/api"
    private val workTypes = listOf(13, 14, 15, 16)

    override fun OkHttpClient.Builder.configureClient() = rateLimit(1) { it.host == apiUrl.toHttpUrl().host }
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            val imageIds = request.url.fragment?.split('/')?.mapNotNull { it.toIntOrNull() }
            if (response.code != 403 || imageIds == null || imageIds.size !in listOf(1, 3) || request.url.host != storageHost) return@addInterceptor response

            // Image signatures expire after an hour; the app can retain covers and chapter pages longer.
            val imageUrl = try {
                runBlocking {
                    val workId = imageIds[0]
                    if (imageIds.size == 1) {
                        client.get("$apiUrl/home/works/$workId", Fetch.XHR).parseAs<ApiResponse<WorkDetails>>().data.coverUrl
                    } else {
                        client.get("$apiUrl/home/works/$workId/chapters/${imageIds[1]}", Fetch.XHR)
                            .parseAs<ApiResponse<Reader>>().data.chapter.images.firstOrNull { it.pageIndex == imageIds[2] }?.imageUrl
                    }
                }
            } catch (exception: Exception) {
                response.close()
                throw IOException("Unable to refresh image", exception)
            } ?: return@addInterceptor response
            response.close()
            chain.proceed(request.newBuilder().url(imageUrl).build())
        }

    private val storageHost = "ichijou.5e9b7ae62dde081ed360e85531f7d92e.r2.cloudflarestorage.com"

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(
        fetchCatalogue().sortedByDescending { it.viewCount }.map { it.toSManga() },
        false,
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage = coroutineScope {
        val catalogue = async { fetchCatalogue().associateBy { it.id } }
        val home = client.get("$apiUrl/home", Fetch.XHR).parseAs<ApiResponse<Home>>().data
        val works = catalogue.await()
        MangasPage(home.newChaptersMangas.distinctBy { it.workId }.mapNotNull { works[it.workId]?.toSManga() }, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val type = filters.firstInstanceOrNull<TypeFilter>()?.state ?: 0
        val works = fetchCatalogue(query, if (type == 0) workTypes else listOf(workTypes[type - 1]))
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.selectedGenre
        val filtered = if (genre == null) works else works.filter { it.hasGenre(genre) }
        val sorted = when (filters.firstInstanceOrNull<SortFilter>()?.state) {
            1 -> filtered.sortedBy { it.title.lowercase() }
            2 -> filtered.sortedByDescending { it.title.lowercase() }
            else -> filtered.sortedByDescending { it.viewCount }
        }
        return MangasPage(sorted.map { it.toSManga() }, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "obras") return null
        val id = url.pathSegments.getOrNull(1)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: return null
        val details = client.get("$apiUrl/home/works/$id", Fetch.XHR).parseAs<ApiResponse<WorkDetails>>().data
        if (details.type.code !in listOf("manga", "manhwa", "manhua", "one_shot")) return null
        val manga = details.toSManga(emptyList(), emptyList(), emptyList())
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val id = getMangaUrl(manga).toHttpUrl().pathSegments[1].takeWhile { it.isDigit() }
        val scan = async { client.get("$apiUrl/home/scans/work/$id", Fetch.XHR).parseAs<ApiResponse<Scan?>>().data }
        val joints = async { client.get("$apiUrl/home/getWorkJoints/work/$id", Fetch.XHR).parseAs<ApiResponse<List<Joint>>>().data }
        val groups = async { (listOfNotNull(scan.await()?.name) + joints.await().map { it.scan.name }).distinct() }
        val details = if (fetchDetails) {
            async {
                val work = async { client.get("$apiUrl/home/works/$id", Fetch.XHR).parseAs<ApiResponse<WorkDetails>>().data }
                val genres = async { client.get("$apiUrl/home/genres/work/$id", Fetch.XHR).parseAs<ApiResponse<List<NamedValue>>>().data }
                val alternatives = async {
                    client.get("$apiUrl/home/getAlternativeNames/work/$id", Fetch.XHR).parseAs<ApiResponse<List<AlternativeName>>>().data
                }
                work.await().toSManga(genres.await(), alternatives.await(), groups.await())
            }
        } else {
            null
        }
        val chapterList = if (fetchChapters) {
            async {
                client.get("$apiUrl/home/getContentWork/work/$id", Fetch.XHR).parseAs<ApiResponse<WorkContent>>().data
                    .toChapters(manga.url, groups.await().joinToString(" & "))
            }
        } else {
            null
        }
        SMangaUpdate(details?.await() ?: manga, chapterList?.await() ?: chapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val segments = getChapterUrl(chapter).toHttpUrl().pathSegments
        val workId = segments[1].takeWhile { it.isDigit() }
        val chapterId = segments[3].takeWhile { it.isDigit() }
        return client.get("$apiUrl/home/works/$workId/chapters/$chapterId", Fetch.XHR)
            .parseAs<ApiResponse<Reader>>().data.chapter.images.sortedBy { it.pageIndex }
            .mapIndexed { index, image ->
                val imageUrl = image.imageUrl.toHttpUrl().newBuilder().fragment("$workId/$chapterId/${image.pageIndex}").build().toString()
                Page(index, imageUrl = imageUrl)
            }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/home/genres", Fetch.XHR)
        .parseAs<ApiResponse<List<NamedValue>>>().data.toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        buildList {
            add(TypeFilter())
            add(SortFilter())
            val genres = data?.parseAs<List<NamedValue>>()
            if (!genres.isNullOrEmpty()) add(GenreFilter(genres.map { it.name }))
        },
    )

    private suspend fun fetchCatalogue(query: String = "", types: List<Int> = workTypes): List<WorkCard> = coroutineScope {
        // The API accepts only one type per request. Read each complete list before combining their sorts.
        types.map { type ->
            async {
                buildList {
                    var page = 1
                    do {
                        val url = "$apiUrl/home/explore".toHttpUrl().newBuilder()
                            .addQueryParameter("page", page.toString())
                            .addQueryParameter("limit", "24")
                            .addQueryParameter("type", type.toString())
                            .addQueryParameter("search", query)
                            .addQueryParameter("sortBy", "title")
                            .addQueryParameter("sortOrder", "ASC")
                            .build()
                        val catalogue = client.get(url, Fetch.XHR).parseAs<ApiResponse<Catalogue>>().data
                        addAll(catalogue.data)
                        page++
                    } while (catalogue.meta.hasNext)
                }
            }
        }.awaitAll().flatten()
    }
}
