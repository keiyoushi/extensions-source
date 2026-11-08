package eu.kanade.tachiyomi.extension.ar.comicsgate

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
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.parser.Parser

@Source
abstract class ComicsGate : KeiSource() {

    override val supportsLatest = false

    private fun postsUrl() = "$baseUrl/home/wp-json/wp/v2/posts".toHttpUrl().newBuilder()
        .addQueryParameter("categories", MAGAZINE_CATEGORY)
        .addQueryParameter("_fields", POST_FIELDS)

    override suspend fun getPopularManga(page: Int) = getIssues(page, "")

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = getIssues(page, query.trim())

    private suspend fun getIssues(page: Int, query: String): MangasPage {
        val url = postsUrl()
            .addQueryParameter("per_page", "20")
            .addQueryParameter("page", page.toString())
            .apply { if (query.isNotEmpty()) addQueryParameter("search", query) }
            .build()
        val response = client.get(url, Fetch.XHR)
        val totalPages = response.header("X-WP-TotalPages")!!.toInt()
        return MangasPage(response.parseAs<List<Post>>().map { it.toSManga() }, page < totalPages)
    }

    // Post urls: /home/<year>/<month>/<day>/<slug>/
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.") || url.pathSegments[0] != "home") return null
        val slug = url.pathSegments.getOrNull(4)?.takeIf { it.isNotEmpty() } ?: return null
        return getPost(slug)?.toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val post = getPost(getMangaUrl(manga).toHttpUrl().pathSegments[4])!!
        return SMangaUpdate(post.toSManga().apply { url = manga.url }, listOf(post.toSChapter()))
    }

    private suspend fun getPost(slug: String): Post? {
        val url = postsUrl().addQueryParameter("slug", slug).build()
        return client.get(url, Fetch.XHR).parseAs<List<Post>>().firstOrNull()
    }

    // Each issue is read in an aXmag page-flip reader: setting.xml gives the page count, m/<n>.jpg are the full-size pages
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val readerUrl = getChapterUrl(chapter).toHttpUrl()
        val pageCount = client.get(readerUrl.resolve("setting.xml")!!, Fetch.XHR.from(readerUrl.toString()))
            .asJsoup(Parser.xmlParser())
            .selectFirst("pagecount")!!.text().toInt()
        return (1..pageCount).map { Page(it - 1, imageUrl = readerUrl.resolve("m/$it.jpg")!!.toString()) }
    }

    companion object {
        private const val MAGAZINE_CATEGORY = "21"
        private const val POST_FIELDS = "link,title,content,date_gmt"
    }
}
