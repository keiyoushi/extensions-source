package eu.kanade.tachiyomi.extension.en.mangapdf

import eu.kanade.tachiyomi.source.UnmeteredSource
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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class MangaPdf :
    KeiSource(),
    UnmeteredSource {

    private val apiUrl by lazy { API_BASE_URL.toHttpUrl() }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor { chain ->
        val request = chain.request()
        val isApiRequest =
            request.url.scheme == apiUrl.scheme &&
                request.url.host == apiUrl.host &&
                request.url.port == apiUrl.port

        if (isApiRequest) {
            chain.proceed(
                request.newBuilder()
                    .header("X-Client", "mihon-extension")
                    .build(),
            )
        } else {
            chain.proceed(request)
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage = client.get(
        popularUrl(page),
    ).parseAs<MangaListResponse>()
        .toMangasPage()

    override suspend fun getLatestUpdates(page: Int): MangasPage = client.get(
        latestUrl(page),
    ).parseAs<MangaListResponse>()
        .toMangasPage()

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage = client.get(
        searchUrl(query, page),
    ).parseAs<MangaListResponse>()
        .toMangasPage()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = client.get(
        mangaUrl(manga.url),
    ).parseAs<MangaUpdateResponse>()
        .toSMangaUpdate()

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(
        pagesUrl(chapter.url),
    ).parseAs<PageListResponse>()
        .toPages()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/chapter/${chapter.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val websiteHost = baseUrl.toHttpUrl().host
        if (url.host != websiteHost) return null

        val id = url.pathSegments.lastOrNull { it.isNotBlank() } ?: return null

        return client.get(
            mangaUrl(id),
        ).parseAs<MangaUpdateResponse>()
            .toSMangaUpdate()
            .manga
    }
}
