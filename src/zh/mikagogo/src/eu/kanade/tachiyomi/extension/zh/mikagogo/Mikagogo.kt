package eu.kanade.tachiyomi.extension.zh.mikagogo

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.tryParseDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Mikagogo : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getPostList(page, "0", "views")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getPostList(page, "0", "date")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val category = filters.firstInstance<CategoryFilter>().selected
        if (query.isBlank()) {
            return getPostList(page, category, filters.firstInstance<SortFilter>().selected)
        }
        val url = baseUrl.toHttpUrl().newBuilder()
            .apply { if (page > 1) addPathSegment("page").addPathSegment(page.toString()) }
            .addQueryParameter("s", query.trim())
            .apply { if (category != "0") addQueryParameter("cat", category) }
            .build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        return MangasPage(postMangas(document), document.selectFirst("a.next.page-numbers") != null)
    }

    // The sorted and filtered lists of the site's category pages
    private suspend fun getPostList(page: Int, category: String, orderBy: String): MangasPage {
        val body = FormBody.Builder()
            .add("action", "postlist_newajax")
            .add("type", "sxpost")
            .add("cat", category)
            .add("term", "category")
            .add("paged", page.toString())
            .add("orderby", orderBy)
            .build()
        val list = client.post("$baseUrl/wp-admin/admin-ajax.php", Fetch.XHR, body).parseAs<PostListResponse>().data
        val mangas = list.html?.let { postMangas(Jsoup.parseBodyFragment(it, baseUrl)) }.orEmpty()
        return MangasPage(mangas, list.hasNextPage)
    }

    private fun postMangas(document: Document) = document.select(".kzpost-data").mapNotNull { item ->
        // A few posts have neither a title nor images
        val title = item.selectFirst(".posr-tit")!!.textOrNull() ?: return@mapNotNull null
        SManga.create().apply {
            url = item.selectFirst("a")!!.absUrl("href").toHttpUrl().pathSegments[0]
            this.title = title
            thumbnail_url = item.selectFirst("img.cardImage")?.absUrl("data-original")
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        val id = url.pathSegments[0].takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: return null
        val manga = SManga.create().apply { this.url = id }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val content = document.selectFirst("#content .content_left")!!
        manga.apply {
            title = document.selectFirst(".kz-single-data h1")!!.text()
            genre = document.select(".kz-post_tags_list a").map { it.text() }.distinct().joinToString().ifEmpty { null }
            description = MarkdownDescription.Builder()
                .description(content.clone().apply { select(NOT_DESCRIPTION).remove() })
                .build()
            thumbnail_url = content.selectFirst("img")?.absUrl("src")
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Gallery"
            date_upload = DATE_FORMAT.tryParseDateTime(document.selectFirst(".kz-post-meta > span:matchesOwn(^\\d{4}-)")?.text(), ZONE)
        }
        return SMangaUpdate(manga, listOf(chapter))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = getChapterUrl(chapter).toHttpUrl()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        // Long posts are split over WordPress page breaks: /<id>/2, /<id>/3...
        val lastPage = document.select(".page_imges a.page-numbers").mapNotNull { it.ownText().toIntOrNull() }.maxOrNull() ?: 1
        val otherPages = coroutineScope {
            (2..lastPage).map { page ->
                async { client.get(url.newBuilder().addPathSegment(page.toString()).build(), Fetch.DOCUMENT).asJsoup() }
            }.awaitAll()
        }
        // Galleries show previews: their HD images are a download for members or for credits
        return (listOf(document) + otherPages).flatMap { it.select("#content .content_left img") }
            .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl.toHttpUrl().newBuilder().addPathSegment(manga.url).build().toString()

    override fun getChapterUrl(chapter: SChapter): String = baseUrl.toHttpUrl().newBuilder().addPathSegment(chapter.url).build().toString()

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("关键词搜索时排序无效"),
        CategoryFilter(),
        SortFilter(),
    )

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-M-d HH:mm:ss", Locale.ROOT)
        private val ZONE = ZoneId.of("Asia/Shanghai")

        // The gallery, the links to the post's other pages, the archive download of 插画精选 posts and the like buttons
        private const val NOT_DESCRIPTION = ".horizontal-image-gallery, .page_imges, h2.kj, h2.kj ~ p:not(:has(img)), .guest_down, .kz-dtpost-div"
    }
}
