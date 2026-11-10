package eu.kanade.tachiyomi.extension.zh.yiqiqimanhua

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Duration.Companion.seconds

@Source
abstract class YiqiqiManhua : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage {
        // The view ranking (漫畫觀看排行) is only in the sidebar of the post pages
        val newestPostUrl = restUrl("/wp/v2/posts")
            .addQueryParameter("per_page", "1")
            .addQueryParameter("_fields", "link")
            .build()
        val newestPost = client.get(newestPostUrl, Fetch.XHR).parseAs<List<PostLink>>().first()
        val document = client.get(newestPost.link, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("#hot_post_widget li").map { item ->
            postManga(item.selectFirst(".new-title a")!!, item.selectFirst("img"))
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = getPostList(listUrl("", page).build())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val category = filters.firstInstanceOrNull<CategoryFilter>()?.selected
        val url = if (query.isNotBlank()) {
            listUrl("", page)
                .addQueryParameter("s", query.trim())
                .apply { category?.let { addQueryParameter("cat", it.id.toString()) } }
        } else {
            listUrl(category?.path.orEmpty(), page)
        }
        return getPostList(url.build())
    }

    private suspend fun getPostList(url: HttpUrl): MangasPage {
        val document = getListDocument(url)
        val mangas = document.select("#main article").map { article ->
            postManga(article.selectFirst(".grid-title a")!!, article.selectFirst("figure img"))
        }
        return MangasPage(mangas, document.selectFirst("a.next.page-numbers") != null)
    }

    // A search right after other visits gets a 429 with the wait in Retry-After (up to 30 seconds)
    private suspend fun getListDocument(url: HttpUrl): Document {
        val response = client.get(url, Fetch.DOCUMENT, ensureSuccess = false)
        val retryAfter = response.header("Retry-After")?.toIntOrNull()
        if (response.code == 429 && retryAfter != null) {
            response.close()
            delay(retryAfter.seconds)
            return client.get(url, Fetch.DOCUMENT).asJsoup()
        }
        if (!response.isSuccessful) {
            response.close()
            throw HttpException(response.code)
        }
        return response.asJsoup()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return null
        val id = url.queryParameter("p") ?: POST_ID.find(url.encodedPath)?.groupValues?.get(1) ?: return null
        val manga = SManga.create().apply { this.url = id }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val post = getPost(manga.url)
        return SMangaUpdate(post.toSManga(manga, baseUrl), listOf(post.toSChapter(manga.url)))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = getPost(chapter.url).imageUrls(baseUrl)
        .mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }

    override fun getMangaUrl(manga: SManga): String = postUrl(manga.url)

    override fun getChapterUrl(chapter: SChapter): String = postUrl(chapter.url)

    override fun getFilterList(data: JsonElement?) = FilterList(CategoryFilter())

    private suspend fun getPost(id: String): Post {
        val url = restUrl("/wp/v2/posts/$id")
            .addQueryParameter("_fields", "title,date_gmt,categories,content")
            .build()
        return client.get(url, Fetch.XHR).parseAs()
    }

    // The site doesn't serve /wp-json/, but its REST API answers on ?rest_route=
    private fun restUrl(route: String) = baseUrl.toHttpUrl().newBuilder().addQueryParameter("rest_route", route)

    private fun listUrl(path: String, page: Int) = baseUrl.toHttpUrl().newBuilder()
        .addPathSegments(path)
        .apply { if (page > 1) addPathSegment("page").addPathSegment(page.toString()) }
        .addPathSegment("")

    private fun postUrl(id: String) = baseUrl.toHttpUrl().newBuilder().addQueryParameter("p", id).build().toString()

    private fun postManga(link: Element, image: Element?) = SManga.create().apply {
        url = POST_ID.find(link.absUrl("href"))!!.groupValues[1]
        title = link.text()
        // Listings show a timthumb.php crop of the first page
        thumbnail_url = image?.absUrl("src")?.toHttpUrlOrNull()?.queryParameter("src")?.toSecureUrl()
    }

    companion object {
        private val POST_ID = Regex("""/(\d+)\.html""")
    }
}
