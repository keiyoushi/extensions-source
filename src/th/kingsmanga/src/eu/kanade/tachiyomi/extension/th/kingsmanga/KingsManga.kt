package eu.kanade.tachiyomi.extension.th.kingsmanga

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class KingsManga : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/hot-manga/", Fetch.DOCUMENT).asJsoup()
        // The weekly and monthly charts of the manga tab
        val mangas = document.select("#manga .list-group a[href*=/manga/]").map { link ->
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
            }
        }.distinctBy { it.url }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = getUpdates("", page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return getUpdates(filters.firstInstance<UpdatesFilter>().value, page)

        // Series are the site's post categories, which its WordPress API searches
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("wp-json/wp/v2/categories")
            .addQueryParameter("search", query.trim())
            .addQueryParameter("per_page", PAGE_SIZE.toString())
            .addQueryParameter("page", page.toString())
            .addQueryParameter("_fields", "name,link")
            .build()
        val response = client.get(url, Fetch.XHR)
        val totalPages = response.header("x-wp-totalpages")?.toIntOrNull() ?: 0
        val mangas = response.parseAs<List<CategoryDto>>().map { it.toSManga() }
        return MangasPage(mangas, page < totalPages)
    }

    // The update lists show chapters, each with its series and its first page
    private suspend fun getUpdates(path: String, page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .apply { if (path.isNotEmpty()) addPathSegments(path) }
            .apply { if (page > 1) addPathSegments("page/$page/") }
            .build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".post-body-alignment").map { post ->
            SManga.create().apply {
                val series = post.selectFirst("a.post-footer-category")!!
                setUrlWithoutDomain(series.absUrl("href"))
                title = series.text()
                thumbnail_url = post.selectFirst(".home-box[data-bg]")?.attr("data-bg")
                    ?.let { BACKGROUND_URL_REGEX.find(it)?.groupValues?.get(1) }
            }
        }.distinctBy { it.url }
        return MangasPage(mangas, document.selectFirst(".wp-pagenavi a.nextpostslink") != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return null
        val seriesUrl = if (url.pathSegments.firstOrNull() == "manga") {
            url
        } else {
            // A chapter's page names its series in the breadcrumbs
            val document = client.get(url.newBuilder().host(baseUrl.toHttpUrl().host).build(), Fetch.DOCUMENT).asJsoup()
            document.selectFirst("#breadcrumbs a[rel~=category]")?.absUrl("href")?.toHttpUrl() ?: return null
        }
        val slug = seriesUrl.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = "/manga/$slug/" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val chapterList = document.select("table.table tbody tr td a[href]").map { link ->
            SChapter.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                name = link.text().removePrefix("อ่าน").trim()
            }
        }
        val details = document.toMangaDetails(manga)
        // The series covers are gone (404) or one placeholder for all: the newest chapter's first page stands in, as on the site's home page
        if (fetchDetails) {
            chapterList.firstOrNull()?.let { newest ->
                details.thumbnail_url = client.get(getChapterUrl(newest), Fetch.DOCUMENT).asJsoup().pageImages().firstOrNull()
            }
        }
        return SMangaUpdate(details, chapterList)
    }

    private fun Document.toMangaDetails(manga: SManga) = SManga.create().apply {
        url = manga.url
        title = selectFirst("h1.title-heading-left")!!.text()
        // The summary panel says "no summary for <title>" when there is none
        val summary = select(".panel-danger .panel-danger").firstOrNull { it.selectFirst(".panel-heading")?.text()?.startsWith("เรื่องย่อ") == true }
            ?.selectFirst(".panel-body")
            ?.takeUnless { it.text().startsWith("ไม่มีเรื่องย่อ") }
        description = MarkdownDescription.Builder()
            .description(summary)
            .build()
        thumbnail_url = manga.thumbnail_url
        initialized = true
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .pageImages()
        .mapIndexed { i, url -> Page(i, imageUrl = url) }

    private fun Document.pageImages() = select(".reader-content .post-content img[src]").map { it.absUrl("src") }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("ตัวกรองไม่ทำงานเมื่อค้นหา"),
        UpdatesFilter(),
    )

    companion object {
        private const val PAGE_SIZE = 24

        // Greedy: file names can hold parentheses
        private val BACKGROUND_URL_REGEX = Regex("""url\((.+)\)""")
    }
}
