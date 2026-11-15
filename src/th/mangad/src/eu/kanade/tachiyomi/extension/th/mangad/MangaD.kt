package eu.kanade.tachiyomi.extension.th.mangad

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
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.int
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class MangaD : KeiSource() {

    // The site's manga list holds every series, with its cover, on one page
    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(getMangaList(), false)

    // The newest chapters (posts) give their series in order. With so few series, further pages would only repeat them
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = apiUrl("posts")
            .addQueryParameter("per_page", "100")
            .addQueryParameter("_fields", "link")
            .build()
        val seriesUrls = client.get(url, Fetch.XHR).parseAs<List<PostLinkDto>>().map { it.seriesUrl(MANGA_LIST_NAME) }.distinct()
        val series = getMangaList().associateBy { it.url }
        return MangasPage(seriesUrls.mapNotNull { series[it] }, false)
    }

    // The manga list is short (about 70 series): search goes through it
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val terms = query.normalized()
        return MangasPage(getMangaList().filter { terms in it.title.normalized() }, false)
    }

    private suspend fun getMangaList(): List<SManga> = client.get(baseUrl.toHttpUrl().newBuilder().addPathSegments("$MANGA_LIST_NAME/").build(), Fetch.DOCUMENT).asJsoup()
        .select("figure.manga-cover-item")
        .map { item ->
            SManga.create().apply {
                val link = item.selectFirst("figcaption a")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = item.selectFirst("img")?.absUrl("src")
            }
        }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        // Series sit under /category/<list>/<series>/, their chapters under /<list>/<series>/<chapter>/
        val index = when {
            segments.getOrNull(0) == "category" && segments.getOrNull(1) == MANGA_LIST_NAME -> 2
            segments.getOrNull(0) == MANGA_LIST_NAME -> 1
            else -> return null
        }
        val slug = segments.getOrNull(index)?.takeIf { it.isNotEmpty() } ?: return null
        // Decoded, like the paths setUrlWithoutDomain keeps
        val manga = SManga.create().apply { this.url = "/category/$MANGA_LIST_NAME/$slug/" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        // The series' category gives its details, and its id lists its chapters: the memo keeps the id for updates
        val category = if (fetchDetails || manga.memo["id"] == null) {
            val slug = getMangaUrl(manga).toHttpUrl().encodedPathSegments.last { it.isNotEmpty() }
            val categoryUrl = apiUrl("categories")
                .addEncodedQueryParameter("slug", slug)
                .addQueryParameter("_fields", "id,name,description")
                .build()
            client.get(categoryUrl, Fetch.XHR).parseAs<List<CategoryDto>>().single()
        } else {
            null
        }
        val details = category?.let {
            SManga.create().apply {
                url = manga.url
                title = it.name
                description = MarkdownDescription.Builder()
                    .markdown(it.description)
                    .build()
                thumbnail_url = manga.thumbnail_url
                    ?: getMangaList().firstOrNull { item -> item.url == manga.url }?.thumbnail_url
                memo = buildJsonObject { put("id", it.id) }
                initialized = true
            }
        } ?: manga
        if (!fetchChapters) return SMangaUpdate(details, chapters)

        // Chapters are the category's posts, 100 at a time
        val categoryId = category?.id ?: manga.memo["id"]!!.int
        val chapterList = mutableListOf<SChapter>()
        var page = 1
        do {
            val postsUrl = apiUrl("posts")
                .addQueryParameter("categories", categoryId.toString())
                .addQueryParameter("per_page", "100")
                .addQueryParameter("page", page.toString())
                .addQueryParameter("_fields", "date_gmt,title,link")
                .build()
            val response = client.get(postsUrl, Fetch.XHR)
            val totalPages = response.header("x-wp-totalpages")?.toIntOrNull() ?: 1
            response.parseAs<List<PostDto>>().mapTo(chapterList) { it.toSChapter() }
        } while (page++ < totalPages)
        return SMangaUpdate(details, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select(".entry-content img[src]")
        // Covers of other series follow the chapter
        .filter { it.closest(".manga-d-related-grid") == null }
        // Some chapters still link their pages over http, which the image host also serves over https
        .mapIndexed { i, img -> Page(i, imageUrl = img.absUrl("src").replaceFirst("http://", "https://")) }

    override fun getFilterList(data: JsonElement?) = FilterList()

    private fun apiUrl(path: String) = baseUrl.toHttpUrl().newBuilder().addPathSegments("wp-json/wp/v2/$path")

    private fun String.normalized() = replace(WHITESPACE_REGEX, "").lowercase()

    companion object {
        // The site's manga list, whose path holds every series and chapter
        private const val MANGA_LIST_NAME = "รายชื่อมังงะ"
        private val WHITESPACE_REGEX = Regex("""\s+""")
    }
}
