package eu.kanade.tachiyomi.extension.all.yskcomics

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
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Source
abstract class YSKComics : KeiSource() {
    private val apiBaseUrl = "https://api.ysk-comics.com"

    override fun Headers.Builder.configureHeaders(): Headers.Builder = set("x-localization", lang)

    // ---

    override suspend fun getPopularManga(page: Int): MangasPage {
        val data = client.get("$baseUrl/api/home/best-comics").parseAs<PopularDto>().data
        return MangasPage(
            mangas = data.map { it.toSManga(lang) },
            hasNextPage = false,
        )
    }

    // ---

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val data = client.get("$baseUrl/api/home/latest-comics?page=$page").parseAs<LatestDto>().data
        return MangasPage(
            mangas = data.dataMessages.map { it.toSManga(lang) },
            hasNextPage = data.meta.linkNext != null,
        )
    }

    // ---

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != lang) {
            return null
        }

        val manga = SManga.create().apply {
            setUrlWithoutDomain(url.toString())
        }

        return fetchMangaDetails(manga)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.trim().length < 3) throw Exception("Search query must be at least 3 characters")

        val url = "$apiBaseUrl/api/v1/search-comics-home".toHttpUrl().newBuilder()
            .addQueryParameter("name", query)
            .build()

        val data = client.get(url).parseAs<SearchDto>().data
        return MangasPage(
            mangas = data.map { it.toSManga(lang) },
            hasNextPage = false,
        )
    }

    // ---

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { if (fetchDetails) fetchMangaDetails(manga) else manga }
        val chapterList = async { if (fetchChapters) fetchChapterList(manga) else chapters }

        SMangaUpdate(details.await(), chapterList.await())
    }

    private suspend fun fetchMangaDetails(manga: SManga): SManga {
        val slug = extractSlug(manga.url)
        return client.get("$baseUrl/api/comic/$slug").parseAs<DetailsDto>().data.toSManga(lang)
    }

    // ---

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        val slug = extractSlug(manga.url)
        return buildList {
            var page = 1
            do {
                val data = client.get("$baseUrl/api/comic/chapter/$slug?page=$page").parseAs<ChapterDto>().data
                data.dataMessages.mapTo(this) { it.toSChapter(lang) }
                page++
            } while (data.meta.linkNext != null)
        }.asReversed()
    }

    // ---

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val slug = extractSlug(chapter.url)
        val data = client.get("$baseUrl/api/chapters/images/$slug").parseAs<PageDto>().data
        return data.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    // ---

    private fun extractSlug(path: String): String {
        val url = "$baseUrl$path"
        return url
            .toHttpUrlOrNull()
            ?.pathSegments
            ?.lastOrNull()
            ?: throw Exception("Unable to parse URL:\n$url")
    }
}
