package eu.kanade.tachiyomi.extension.en.mangapdf

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
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
    ConfigurableSource,
    UnmeteredSource {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder =
        addInterceptor { chain ->
            val request = chain.request()
            val configuredBase = normalizeBaseUrl(baseUrl).toHttpUrl()

            val sameOrigin =
                request.url.scheme == configuredBase.scheme &&
                    request.url.host == configuredBase.host &&
                    request.url.port == configuredBase.port

            if (!sameOrigin) {
                chain.proceed(request)
            } else {
                chain.proceed(
                    request.newBuilder()
                        .header("X-Client", "mihon-extension")
                        .build(),
                )
            }
        }

    override suspend fun getPopularManga(page: Int): MangasPage =
        client.get(
            popularUrl(baseUrl, page),
        ).parseAs<MangaListResponse>()
            .toMangasPage()

    override suspend fun getLatestUpdates(page: Int): MangasPage =
        client.get(
            latestUrl(baseUrl, page),
        ).parseAs<MangaListResponse>()
            .toMangasPage()

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage =
        client.get(
            searchUrl(baseUrl, query, page),
        ).parseAs<MangaListResponse>()
            .toMangasPage()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate =
        client.get(
            mangaUrl(baseUrl, manga.url),
        ).parseAs<MangaUpdateResponse>()
            .toSMangaUpdate()

    override suspend fun getPageList(chapter: SChapter): List<Page> =
        client.get(
            pagesUrl(baseUrl, chapter.url),
        ).parseAs<PageListResponse>()
            .toPages()

    override fun getMangaUrl(manga: SManga): String =
        mangaUrl(baseUrl, manga.url).toString()

    override fun getChapterUrl(chapter: SChapter): String =
        pagesUrl(baseUrl, chapter.url).toString()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val id = mangaIdFromUrl(baseUrl, url) ?: return null

        return client.get(
            mangaUrl(baseUrl, id),
        ).parseAs<MangaUpdateResponse>()
            .toSMangaUpdate()
            .manga
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {}
}
