package eu.kanade.tachiyomi.extension.all.yskcomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.lang.Exception

class YSKComics(
    override val lang: String,
) : HttpSource() {
    override val name = "YSK Comics"
    override val baseUrl = "https://www.ysk-comics.com"
    private val apiBaseUrl = "https://api.ysk-comics.com"
    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient
    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("x-localization", lang)

    // ---

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/home/best-comics", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<PopularDto>().data
        return MangasPage(
            mangas = data.map { it.toSManga(lang) },
            hasNextPage = false,
        )
    }

    // ---

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/api/home/latest-comics?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val data = response.parseAs<LatestDto>().data
        return MangasPage(
            mangas = data.dataMessages.map { it.toSManga(lang) },
            hasNextPage = data.meta.linkNext != null,
        )
    }

    // ---

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        val httpUrl = query.toHttpUrlOrNull()
            ?: return super.fetchSearchManga(page, query, filters)

        if (httpUrl.pathSegments.firstOrNull() != lang) {
            return Observable.just(
                MangasPage(
                    mangas = emptyList(),
                    hasNextPage = false,
                ),
            )
        }

        val manga = SManga.create().apply {
            setUrlWithoutDomain(httpUrl.toString())
        }

        return fetchMangaDetails(manga).map {
            MangasPage(
                mangas = listOf(it),
                hasNextPage = false,
            )
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiBaseUrl/api/v1/search-comics-home".toHttpUrl().newBuilder()
            .addQueryParameter("name", query)
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<SearchDto>().data
        return MangasPage(
            mangas = data.map { it.toSManga(lang) },
            hasNextPage = false,
        )
    }

    // ---

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = extractSlug(manga.url)
        val url = "$baseUrl/api/comic/$slug"
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.parseAs<DetailsDto>().data
        return data.toSManga(lang)
    }

    // ---

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.just(
        buildList {
            var page = 1
            do {
                val request = chapterListRequestPaged(manga, page)
                val response = client.newCall(request).execute()
                val chaptersPage = chapterListParsePaged(response)
                addAll(chaptersPage.chapters)
                page++
            } while (chaptersPage.hasNextPage)
        }.asReversed(),
    )

    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException()

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    private fun chapterListRequestPaged(manga: SManga, page: Int): Request {
        val slug = extractSlug(manga.url)
        val url = "$baseUrl/api/comic/chapter/$slug?page=$page"
        return GET(url, headers)
    }

    private fun chapterListParsePaged(response: Response): ChaptersPage {
        val data = response.parseAs<ChapterDto>().data
        return ChaptersPage(
            chapters = data.dataMessages.map { it.toSChapter(lang) },
            hasNextPage = data.meta.linkNext != null,
        )
    }

    // ---

    override fun pageListRequest(chapter: SChapter): Request {
        val slug = extractSlug(chapter.url)
        val url = "$baseUrl/api/chapters/images/$slug"
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<PageDto>().data
        return data.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ---

    private fun extractSlug(path: String): String {
        val url = "$baseUrl$path"
        return url
            .toHttpUrlOrNull()
            ?.pathSegments
            ?.lastOrNull()
            ?: throw Exception("Unable to parse URL:\n$url")
    }

    private class ChaptersPage(
        val chapters: List<SChapter>,
        val hasNextPage: Boolean,
    )
}
