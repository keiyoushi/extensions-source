package eu.kanade.tachiyomi.extension.en.mangapdf

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
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class MangaPdf : KeiSource() {

    private val apiUrl get() = "https://api.coffeemanga.shop".toHttpUrl()

    private fun apiBuilder(): HttpUrl.Builder = apiUrl.newBuilder()
        .addPathSegment("api")
        .addPathSegment("v1")
        .addPathSegment("mihon")

    override fun Headers.Builder.configureHeaders(): Headers.Builder = add("X-Client", "mihon-extension")

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = apiBuilder()
            .addPathSegment("popular")
            .addQueryParameter("page", page.toString())
            .build()

        return client.get(url).parseAs<MangaListResponse>().toMangasPage()
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = apiBuilder()
            .addPathSegment("latest")
            .addQueryParameter("page", page.toString())
            .build()

        return client.get(url).parseAs<MangaListResponse>().toMangasPage()
    }

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val url = apiBuilder()
            .addPathSegment("search")
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()

        return client.get(url).parseAs<MangaListResponse>().toMangasPage()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = apiBuilder()
            .addPathSegment("manga")
            .addPathSegment(manga.url)
            .build()

        return client.get(url).parseAs<MangaUpdateResponse>().toSMangaUpdate()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = apiBuilder()
            .addPathSegment("chapter")
            .addPathSegment(chapter.url)
            .addPathSegment("pages")
            .build()

        return client.get(url).parseAs<PageListResponse>().toPages()
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/chapter/${chapter.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != apiUrl.host) return null

        val id = url.pathSegments.lastOrNull { it.isNotBlank() } ?: return null

        val apiUrl = apiBuilder()
            .addPathSegment("manga")
            .addPathSegment(id)
            .build()

        return client.get(apiUrl).parseAs<MangaUpdateResponse>().toSMangaUpdate().manga
    }
}
