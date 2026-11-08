package eu.kanade.tachiyomi.extension.en.extrafabulouscomics

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class ExtraFabulousComics : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int) = MangasPage(listOf(createManga()), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val manga = createManga().takeIf { it.title.contains(query.trim(), ignoreCase = true) }
        return MangasPage(listOfNotNull(manga), false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? = createManga().takeIf { url.host == baseUrl.toHttpUrl().host }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ) = SMangaUpdate(
        if (fetchDetails) createManga() else manga,
        if (fetchChapters) fetchChapterList() else chapters,
    )

    private fun createManga() = SManga.create().apply {
        url = "/archive"
        title = name
        author = AUTHOR
        artist = AUTHOR
        description = DESCRIPTION
        thumbnail_url = "$MEDIA_URL/904535_d7187dcb8545431db8f80865f59ef376~mv2.png"
    }

    // Each blog post is one comic
    private suspend fun fetchChapterList(): List<SChapter> {
        val headers = apiHeaders()
        val first = queryPosts(headers, 0)
        val others = coroutineScope {
            (first.posts.size until first.pagingMetadata.total step POSTS_PER_QUERY)
                .map { offset -> async { queryPosts(headers, offset).posts } }
                .awaitAll()
                .flatten()
        }
        val posts = first.posts + others
        // Most titles are the comic's number, but not the latest ones (random digits): number them by position
        return posts.mapIndexed { index, post -> post.toSChapter(posts.size - index) }
    }

    private suspend fun queryPosts(headers: Headers, offset: Int): PostsDto {
        val body = PostsQueryDto(QueryDto(PagingDto(offset, POSTS_PER_QUERY))).toJsonRequestBody()
        return client.post("$baseUrl/_api/communities-blog-node-api/v3/posts/query", headers, Fetch.XHR, body).parseAs()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$baseUrl/_api/blog-frontend-adapter-public/v2/post-page".toHttpUrl().newBuilder()
            .addPathSegment(chapter.url.substringAfterLast('/'))
            .addQueryParameter("languageCode", "en")
            .build()
        val post = client.get(url, apiHeaders(), Fetch.XHR).parseAs<PostPageDto>()
        return post.imageIds().mapIndexed { index, id -> Page(index, imageUrl = "$MEDIA_URL/$id") }
    }

    // The blog API takes the visitor token the site hands out to its blog app
    private suspend fun apiHeaders(): Headers {
        val tokens = client.get("$baseUrl/_api/v1/access-tokens", Fetch.XHR).parseAs<AccessTokensDto>()
        return headersBuilder().set("Authorization", tokens.apps.getValue(BLOG_APP_ID).instance).build()
    }

    companion object {
        private const val POSTS_PER_QUERY = 100
        private const val BLOG_APP_ID = "14bcded7-0066-7c35-14d7-466cb3f09103"
        private const val MEDIA_URL = "https://static.wixstatic.com/media"
        private const val AUTHOR = "Zach M. Stafford"
        private const val DESCRIPTION = "comics from hell and beyond, beyond hell, the field beyond hell where they " +
            "grow corn and shit. Shit comics from the cornfield beyond hell"
    }
}
