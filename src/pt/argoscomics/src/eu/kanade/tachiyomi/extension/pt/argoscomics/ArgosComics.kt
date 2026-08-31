package eu.kanade.tachiyomi.extension.pt.argoscomics

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.extractNextJsRsc
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Source
abstract class ArgosComics : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(3, 2.seconds)
    }

    private val rscHeaders
        get() = headersBuilder().set("rsc", "1").build()

    // ======================== Popular =============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("projetos")
            .addQueryParameter("page", page.toString())
            .build()
        return client.get(url, rscHeaders).extractNextJs<MangasListDto>()!!.toMangasPage()
    }

    // ======================== Latest =============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = client.get(baseUrl, rscHeaders).extractNextJs<LatestMangas>()!!.toMangasPage()

    // ======================== Search =============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val searchHeaders = headers.newBuilder()
            .set("Next-Action", SEARCH_TOKEN)
            .build()
        val payload = listOf(query).toJsonRequestBody()
        val dto = client.post(baseUrl, searchHeaders, payload).extractNextJs<List<MangaDto>>() ?: emptyList()
        return MangasPage(dto.map(MangaDto::toSManga), false)
    }

    // ======================== Details + Chapters =============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val detailsDeferred = async {
            if (fetchDetails) {
                val url = getMangaUrl(manga)
                val payload = url.toHttpUrl().pathSegments.toJsonRequestBody()
                val detailsHeaders = headers.newBuilder()
                    .set("Next-Action", DETAILS_TOKEN)
                    .build()
                client.post(url, detailsHeaders, payload).extractNextJs<MangaDetailsDto>()!!.toSManga()
            } else {
                manga
            }
        }

        val chaptersDeferred = async {
            if (fetchChapters) {
                val url = getMangaUrl(manga)
                val payload = url.toHttpUrl().pathSegments.toJsonRequestBody()
                val chaptersHeaders = headers.newBuilder()
                    .set("Next-Action", CHAPTERS_TOKEN)
                    .build()
                val response = client.post(url, chaptersHeaders, payload)
                val pathSegment = url.substringAfter(baseUrl)
                response.extractNextJs<VolumeChapterDto>()!!.toChapterList(pathSegment)
            } else {
                chapters
            }
        }

        SMangaUpdate(detailsDeferred.await(), chaptersDeferred.await())
    }

    // ======================== Pages =============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = getChapterUrl(chapter)
        val segments = url.toHttpUrl().pathSegments
        val payload = listOf(segments.first(), segments.last()).toJsonRequestBody()
        val pagesHeaders = headers.newBuilder()
            .set("Next-Action", PAGES_TOKEN)
            .build()
        val response = client.post(url, pagesHeaders, payload)
        if (response.request.url.encodedPath == "/login") error("Acesse sua conta")
        val body = response.body.string()
        val dto = body.extractNextJsRsc<MangaDetailsDto>()
        if (dto?.isUpcoming == true) error("Capítulo em desenvolvimento")
        return body.extractNextJsRsc<PagesDto>()!!.toPageList()
    }

    companion object {
        private const val SEARCH_TOKEN = "409ae74984efeccf922164e02e3bcd60d8b0107638"
        private const val CHAPTERS_TOKEN = "606c13e60309ce062fade63ac2f1cc68bbc5dc25f4"
        private const val DETAILS_TOKEN = "60e89cb5963d6bb1b61383872fbfb4cc2726925dd8"
        private const val PAGES_TOKEN = "6062e8559136ee33cc337e5520fb09950c3dced65e"
    }
}
