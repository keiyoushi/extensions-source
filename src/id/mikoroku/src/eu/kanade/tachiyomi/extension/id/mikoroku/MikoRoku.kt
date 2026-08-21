package eu.kanade.tachiyomi.extension.id.mikoroku

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
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class MikoRoku : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(4) {
        it.host == MANGA_FEED_HOST || it.host == CHAPTER_FEED_HOST
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaPage(page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaPage(page)

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage = getMangaPage(page, query.takeIf(String::isNotEmpty))

    private suspend fun getMangaPage(page: Int, query: String? = null): MangasPage {
        val startIndex = (page - 1) * MANGA_PAGE_SIZE + 1
        val response = client.get(
            feedUrl(
                MANGA_FEED_URL,
                startIndex = startIndex,
                maxResults = MANGA_PAGE_SIZE,
                query = query,
            ),
        ).parseAs<BloggerFeedResponse>()
        val mangas = response.feed.entry.map { it.toSManga() }
        val hasNextPage = response.feed.entry.size == MANGA_PAGE_SIZE

        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.lastOrNull() !in setOf("detail", "detail.html")) return null

        val slug = url.queryParameter("slug")?.takeIf(String::isNotEmpty) ?: return null
        return getMangaEntry(slug.replace('-', ' '), slug)?.toSMangaDetails(slug)
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("detail")
        .addQueryParameter("slug", manga.url)
        .build()
        .toString()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaAsync = async {
            if (fetchDetails) {
                getMangaEntry(manga.title, manga.url)?.toSMangaDetails(manga.url)
                    ?: throw Exception("Manga not found: ${manga.title}")
            } else {
                manga
            }
        }

        val chaptersAsync = async {
            if (fetchChapters) {
                getChapterList(manga.title)
            } else {
                chapters
            }
        }

        SMangaUpdate(mangaAsync.await(), chaptersAsync.await())
    }

    private suspend fun getMangaEntry(title: String, slug: String): BloggerEntry? {
        val response = client.get(
            feedUrl(
                MANGA_FEED_URL,
                startIndex = 1,
                maxResults = DETAIL_SEARCH_SIZE,
                query = title,
            ),
        ).parseAs<BloggerFeedResponse>()

        return response.feed.entry.firstOrNull { it.slug == slug }
    }

    private suspend fun getChapterList(mangaTitle: String): List<SChapter> {
        val response = client.get(
            feedUrl(
                CHAPTER_FEED_URL,
                startIndex = 1,
                maxResults = MAX_CHAPTER_RESULTS,
                query = mangaTitle,
            ),
        ).parseAs<BloggerFeedResponse>()

        return response.feed.entry
            .filter { entry -> entry.isChapterFor(mangaTitle) }
            .mapNotNull { entry -> entry.toSChapter() }
            .distinctBy { chapter -> chapter.chapter_number }
            .sortedByDescending { chapter -> chapter.chapter_number }
    }

    override fun getChapterUrl(chapter: SChapter): String = CHAPTER_WEB_URL.toHttpUrl().resolve(chapter.url.substringBefore('#'))!!.toString()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val postId = chapter.url.substringAfterLast('#').takeIf(String::isNotEmpty)
            ?: return emptyList()
        val url = "$CHAPTER_FEED_URL/$postId".toHttpUrl().newBuilder()
            .addQueryParameter("alt", "json")
            .build()
        val entry = client.get(url).parseAs<BloggerEntryResponse>().entry

        return entry.pageUrls().mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    private fun feedUrl(
        endpoint: String,
        startIndex: Int,
        maxResults: Int,
        query: String?,
    ): HttpUrl = endpoint.toHttpUrl().newBuilder()
        .addQueryParameter("alt", "json")
        .addQueryParameter("orderby", "updated")
        .addQueryParameter("start-index", startIndex.toString())
        .addQueryParameter("max-results", maxResults.toString())
        .apply {
            query?.let { addQueryParameter("q", it) }
        }
        .build()

    companion object {
        private const val MANGA_FEED_HOST = "www.mikoroku.top"
        private const val CHAPTER_FEED_HOST = "www.mikodrive.my.id"
        private const val MANGA_FEED_URL = "https://$MANGA_FEED_HOST/feeds/posts/default"
        private const val CHAPTER_FEED_URL = "https://$CHAPTER_FEED_HOST/feeds/posts/default"
        private const val CHAPTER_WEB_URL = "https://$CHAPTER_FEED_HOST/"
        private const val MANGA_PAGE_SIZE = 20
        private const val DETAIL_SEARCH_SIZE = 20
        private const val MAX_CHAPTER_RESULTS = 500
    }
}
