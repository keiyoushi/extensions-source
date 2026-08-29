package eu.kanade.tachiyomi.extension.ar.mangatime

import eu.kanade.tachiyomi.network.POST
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
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import okhttp3.internal.closeQuietly
import java.io.IOException

@Source
abstract class MangaTime : KeiSource() {
    private val limit: Int = 24

    fun toImage(url: String): String {
        val t = url.replace(" ", "%20")
        return when {
            url.startsWith("http") -> t
            else -> "$baseUrl$t"
        }
    }

    private fun endpointUrl(endpoint: String) = "$baseUrl/api/trpc/$endpoint"

    private suspend inline fun <reified I, reified O> trpcQuery(endpoint: String, input: I): O {
        val url = endpointUrl(endpoint).toHttpUrl().newBuilder().apply {
            addQueryParameter("batch", "1")
            addQueryParameter("input", TrpcEnvelope(input).toJsonString())
        }.build()

        return client.get(url).parseAs<List<TrpcResponse<O>>>().first().result.data.json
    }

    private suspend fun searchSeries(page: Int, sortBy: String, query: String? = null): MangasPage {
        val result = trpcQuery<SearchDto, MangaListDto>(
            "search.searchSeries",
            SearchDto(page, limit, sortBy, "desc", query),
        )

        return MangasPage(result.results.map { it.toSManga() }, result.hasMore)
    }

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage = searchSeries(page, "popularity")

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage = searchSeries(page, "recent")

    // Search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = searchSeries(page, "popularity", query)

    // Details & Chapters

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaUrl = getMangaUrl(manga).toHttpUrl()
        val mangaPath = mangaUrl.encodedPath

        val mangaDeferred = async {
            if (!fetchDetails) return@async manga
            trpcQuery<SeriesSlug, SeriesDto>(
                "content.getSeriesBySlug",
                SeriesSlug(mangaUrl.pathSegments[1]),
            ).toSManga()
        }

        val chaptersDeferred = async {
            if (!fetchChapters) return@async chapters
            trpcQuery<ChaptersQuery, ChaptersDto>(
                "content.getChapters",
                ChaptersQuery(mangaUrl.fragment!!, -1),
            ).chapters.map { it.toSChapter(mangaPath) }
        }

        SMangaUpdate(mangaDeferred.await(), chaptersDeferred.await())
    }

    // Pages

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter).toHttpUrl()
        val seriesSlug = chapterUrl.pathSegments[1]
        val chapterNumber = chapterUrl.pathSegments[3].toInt()

        val result = trpcQuery<PagesQuery, PagesDto>(
            "content.getChapterPages",
            PagesQuery(seriesSlug, chapterNumber),
        )

        if (!result.isUnlocked) throw Exception("Chapter is locked")

        trackView(result.seriesId, result.id)

        return result.pages.mapIndexed { i, image ->
            Page(i, imageUrl = toImage(image))
        }
    }

    private fun trackView(seriesId: String, chapterId: String) {
        client.newCall(
            POST(
                endpointUrl("content.trackView"),
                headers,
                TrpcEnvelope(ViewQuery(seriesId, chapterId)).toJsonString().toJsonRequestBody(),
            ),
        ).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) = response.closeQuietly()
            override fun onFailure(call: Call, e: IOException) {}
        })
    }
}
