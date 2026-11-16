package eu.kanade.tachiyomi.extension.ar.mangaswat

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
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Source
abstract class MangaSwat : KeiSource() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(1)
    }

    private fun String.getMangaId(): String = this.removePrefix("/chapters/").substringBefore("/")

    private fun Response.parseMangaList(): MangasPage {
        val data = this.parseAs<MangaListDto>()
        val entries = data.results.map { it.toSManga() }
        return MangasPage(entries, data.hasNext())
    }

    // ========================= Popular =========================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.get("$API_URL/series/?order_by=-followers_count&page=$page", Fetch.XHR)

        return response.parseMangaList()
    }

    // ========================= Latest =========================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val response = client.get("$API_URL/series/releases/?page=$page", Fetch.XHR)

        return response.parseMangaList()
    }

    //  ========================= Search =========================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$API_URL/series/".toHttpUrl().newBuilder().apply {
            addQueryParameter("search", query)
            addQueryParameter("page", page.toString())
        }.build()

        return client.get(url, Fetch.XHR).parseMangaList()
    }

    // ==================== Details & Chapters ====================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaDeferred = async {
            if (!fetchDetails) return@async manga

            client.get("$API_URL/series/${manga.url}/", Fetch.XHR)
                .parseAs<MangaDetailsDto>().toSManga()
        }

        val chaptersDeferred = async {
            if (fetchChapters) getChapterList(manga) else chapters
        }

        SMangaUpdate(
            manga = mangaDeferred.await(),
            chapters = chaptersDeferred.await(),
        )
    }

    private suspend fun getChapterList(manga: SManga): List<SChapter> = buildList {
        var page = 1
        do {
            val url = "$API_URL/chapters/".toHttpUrl().newBuilder().apply {
                addQueryParameter("serie", manga.url)
                addQueryParameter("order_by", "-order")
                addQueryParameter("page_size", "200")
                addQueryParameter("page", (page++).toString())
            }.build()
            val data = client.get(url, Fetch.XHR).parseAs<ChapterListDto>()
            addAll(data.results.map { it.toSChapter() })
        } while (data.next != null)
    }

    // ========================= Pages =========================

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/chapter/${chapter.url.getMangaId()}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get("$API_URL/chapters/${chapter.url.getMangaId()}/", Fetch.XHR)

        val chapter = response.parseAs<PageListDto>()
        return chapter.images.mapIndexed { i, page ->
            Page(i, imageUrl = page.image.toAppHost())
        }
    }

    companion object {
        // The site's mobile app host; the website's images redirect to a banner
        internal const val APP_HOST = "appswat.com"
        private const val API_URL = "https://$APP_HOST/v2/api/v2"
    }
}
