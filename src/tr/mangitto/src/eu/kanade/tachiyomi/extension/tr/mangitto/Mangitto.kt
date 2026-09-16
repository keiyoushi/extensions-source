package eu.kanade.tachiyomi.extension.tr.mangitto

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getString
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class Mangitto : KeiSource() {

    private val apiUrl get() = "$baseUrl/api/manga"

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2)
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val skip = (page - 1) * PAGE_SIZE
        val data = client.get("$apiUrl/populer?skip=$skip&take=$PAGE_SIZE").parseAs<MangttoPopularData>()

        return MangasPage(data.mangas.map { it.toSManga() }, skip + data.mangas.size < data.total)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val skip = (page - 1) * PAGE_SIZE
        val data = client.get("$apiUrl/latest?skip=$skip&take=$PAGE_SIZE").parseAs<MangttoLatestData>()

        // The API returns a list of chapters, so we distinct by slug to avoid duplicates.
        val mangas = data.chapters.map { it.manga }.distinctBy { it.slug }.map { it.toSManga() }

        return MangasPage(mangas, skip + data.chapters.size < data.total)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("q", query)

        filters.firstInstanceOrNull<GenreFilter>()?.getQuery()
            ?.takeIf { it.isNotEmpty() }
            ?.let { url.addQueryParameter("genres", it) }

        if (filters.firstInstanceOrNull<AdultFilter>()?.state == true) {
            url.addQueryParameter("isAdult", "true")
        }

        if (filters.firstInstanceOrNull<CompletedFilter>()?.state == true) {
            url.addQueryParameter("isFinished", "true")
        }

        filters.firstInstanceOrNull<ScoreFilter>()?.state
            ?.takeIf { it.isNotBlank() }
            ?.let { url.addQueryParameter("meanScore", it.trim()) }

        filters.firstInstanceOrNull<DateFilter>()?.state
            ?.takeIf { it.isNotBlank() }
            ?.let { url.addQueryParameter("releaseDate", it.trim()) }

        val data = client.get(url.build()).parseAs<MangttoSearchData>()

        return MangasPage(data.hits.map { it.document.toSManga() }, page * SEARCH_PAGE_SIZE < data.estimatedTotalHits)
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/api/genres").parseAs()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<MangttoGenres>()?.genres.orEmpty()
        return FilterList(
            buildList {
                if (genres.isNotEmpty()) {
                    add(GenreFilter(genres))
                }
                add(AdultFilter())
                add(CompletedFilter())
                add(ScoreFilter())
                add(DateFilter())
            },
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "manga") {
            return null
        }
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null

        return client.get("$apiUrl/$slug").parseAs<MangttoDetailData>().toSManga().apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/manga/${chapter.memo.getString("mangaSlug")}/${chapter.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (fetchDetails) client.get("$apiUrl/${manga.url}").parseAs<MangttoDetailData>().toSManga() else manga
        }
        val chapterList = async {
            if (fetchChapters) fetchAllChapters(manga.url) else chapters
        }

        SMangaUpdate(details.await(), chapterList.await())
    }

    private suspend fun fetchAllChapters(slug: String): List<SChapter> {
        val allChapters = mutableListOf<SChapter>()
        var skip = 0

        while (true) {
            val data = client.get("$apiUrl/$slug/chapters?skip=$skip&take=$PAGE_SIZE").parseAs<MangttoChapterPageData>()

            allChapters.addAll(data.chapters.map { it.toSChapter(slug) })
            skip += data.chapters.size

            if (data.chapters.isEmpty() || skip >= data.total) break
        }

        // The API returns chapters in ascending order.
        return allChapters.reversed()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val slug = chapter.memo.getString("mangaSlug")
        val data = client.get("$apiUrl/$slug/${chapter.url}").parseAs<MangttoPageData>()
        val upload = data.uploads.firstOrNull() ?: return emptyList()

        return (1..upload.fileLength).map { pageNum ->
            Page(pageNum - 1, imageUrl = "${data.cdn}/manga/$slug/${chapter.url}/$pageNum-${upload.fansubId}.webp")
        }
    }

    companion object {
        private const val PAGE_SIZE = 50
        private const val SEARCH_PAGE_SIZE = 42
    }
}
