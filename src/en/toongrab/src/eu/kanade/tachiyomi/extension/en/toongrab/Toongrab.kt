package eu.kanade.tachiyomi.extension.en.toongrab

import eu.kanade.tachiyomi.source.model.Filter
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class Toongrab : KeiSource() {

    // Popular = views descending ("Most Popular"); Latest = the default browse order
    override suspend fun getPopularManga(page: Int): MangasPage = browseMangas(page, "Most Popular")

    override suspend fun getLatestUpdates(page: Int): MangasPage = browseMangas(page, null)

    private suspend fun browseMangas(page: Int, sort: String?): MangasPage {
        val url = "$baseUrl/api/manga/browse".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            if (sort != null) addQueryParameter("sort", sort)
        }.build()
        val response = client.get(url).parseAs<BrowseResponse>()
        return MangasPage(response.data.map { it.toSManga() }, response.hasNextPage(page))
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/api/manga/browse".toHttpUrl().newBuilder().apply {
            addQueryParameter("search", query)
            addQueryParameter("page", page.toString())
            filters.forEach { filter ->
                when (filter) {
                    is UriPartFilter -> filter.takeIf { it.value.isNotEmpty() }
                        ?.let { addQueryParameter(it.param, it.value) }
                    else -> {}
                }
            }
        }.build()

        val response = client.get(url).parseAs<BrowseResponse>()
        return MangasPage(response.data.map { it.toSManga() }, response.hasNextPage(page))
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        StatusFilter(),
    )

    private open class UriPartFilter(
        name: String,
        val param: String,
        private val vals: Array<Pair<String, String>>,
    ) : Filter.Select<String>(name, vals.map { it.first }.toTypedArray()) {
        val value get() = vals[state].second
    }

    private class StatusFilter :
        UriPartFilter(
            "Status",
            "status",
            arrayOf("Any" to "", "Ongoing" to "Ongoing", "Completed" to "Completed", "Hiatus" to "Hiatus"),
        )

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    // Details and chapters live behind separate endpoints; fetch only what each flag asks for
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val detailsDeferred = async {
            if (fetchDetails) client.get("$baseUrl/api/manga/${manga.url}").parseAs<MangaDetails>() else null
        }

        val chapterList = if (fetchChapters) {
            // Chapter API is paginated, capped at 100 per page server-side
            val first = client.get("$baseUrl/api/manga/${manga.url}/chapters?limit=100")
                .parseAs<ChapterListResponse>()
            val rest = (2..first.totalPages).map { page ->
                async {
                    client.get("$baseUrl/api/manga/${manga.url}/chapters?limit=100&page=$page")
                        .parseAs<ChapterListResponse>()
                        .chapters
                }
            }.awaitAll().flatten()
            (first.chapters + rest).map { it.toSChapter(manga.url) }
        } else {
            chapters
        }

        val details = detailsDeferred.await()
        SMangaUpdate(details?.toSManga(manga.url) ?: manga, chapterList)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        val slug = segments.getOrNull(1) ?: return null
        if (segments.firstOrNull() != "manga" || slug.isBlank()) return null
        return SManga.create().apply {
            this.url = slug
            this.title = slug.replace('-', ' ')
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val segments = chapter.url.trimStart('/').split('/')
        val slug = segments[1]
        val number = segments[2].removePrefix("chapter-")

        val response = client.get("$baseUrl/api/manga/$slug/chapter/$number").parseAs<ChapterImages>()
        return response.toPageList()
    }
}
