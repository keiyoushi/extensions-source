package eu.kanade.tachiyomi.extension.all.junmeitu

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale

class Junmeitu : HttpSource() {
    override val lang = "all"
    override val name = "Junmeitu"
    override val supportsLatest = true
    override val id = 4721197766605490540

    override val baseUrl = "https://meijuntu.com"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/beauty/index-$page.html", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/beauty/hot-$page.html", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".pic-list > ul > li").map { element ->
            SManga.create().apply {
                title = element.select("p").text()
                thumbnail_url = element.select("img").attr("abs:src")
                setUrlWithoutDomain(element.select("a").attr("abs:href"))
            }
        }
        val hasNextPage = document.selectFirst("span + a  + a") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val tagFilter = filters.firstInstanceOrNull<TagFilter>()
        val modelFilter = filters.firstInstanceOrNull<ModelFilter>()
        val groupFilter = filters.firstInstanceOrNull<GroupFilter>()
        val categoryFilter = filters.firstInstanceOrNull<CategoryFilter>()
        val sortFilter = filters.firstInstanceOrNull<SortFilter>()

        return when {
            query.isNotEmpty() -> GET("$baseUrl/search/$query-$page.html", headers)
            tagFilter != null && tagFilter.state.isNotEmpty() -> GET("$baseUrl/tags/${tagFilter.state}-${categoryFilter?.selected ?: "6"}-$page.html", headers)
            modelFilter != null && modelFilter.state.isNotEmpty() -> GET("$baseUrl/model/${modelFilter.state}-$page.html", headers)
            groupFilter != null && groupFilter.state.isNotEmpty() -> GET("$baseUrl/xzjg/${groupFilter.state}-$page.html", headers)
            categoryFilter != null && categoryFilter.state != 0 -> GET("$baseUrl/${categoryFilter.slug}/${sortFilter?.selected ?: "index"}-$page.html", headers)
            sortFilter != null && sortFilter.state != 0 -> GET("$baseUrl/${categoryFilter?.slug ?: "beauty"}/${sortFilter.selected}-$page.html", headers)
            else -> latestUpdatesRequest(page)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst(".news-title, .title")?.text() ?: ""
            description = buildString {
                append(document.select(".news-info, .picture-details").joinToString(" ") { it.text() })
                append("\n")
                append(document.select(".introduce").text())
            }
            genre = document.select(".relation_tags > a").joinToString(", ") { it.text() }
            status = SManga.COMPLETED
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapter = SChapter.create().apply {
            val urlElement = document.selectFirst(".position a:last-child")
            val href = urlElement?.attr("abs:href")
            setUrlWithoutDomain(if (!href.isNullOrEmpty()) href else response.request.url.toString())
            name = "Gallery"

            val dateText = document.select(".picture-details span.gao:contains(日期)").text().substringAfter("日期:").trim()
            date_upload = dateFormat.tryParse(dateText)
        }
        return listOf(chapter)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pages = mutableListOf<Page>()

        val newsBody = document.selectFirst(".news-body")
        if (newsBody != null) {
            newsBody.select("img").forEachIndexed { index, img ->
                val imgUrl = img.attr("abs:data-original").takeIf { it.isNotEmpty() }
                    ?: img.attr("abs:data-src").takeIf { it.isNotEmpty() }
                    ?: img.attr("abs:data-lazy-src").takeIf { it.isNotEmpty() }
                    ?: img.attr("abs:src")
                pages.add(Page(index, imageUrl = imgUrl))
            }
            return pages
        }

        val numPagesText = document.select(".pages > a:nth-last-of-type(2)").text()
        val numPages = numPagesText.toIntOrNull()
            ?: document.select(".pages a").mapNotNull { it.text().toIntOrNull() }.maxOrNull()
            ?: 1

        val scriptData = document.select("script").find { it.data().contains("pc_cid") }?.data()

        if (scriptData != null) {
            val categoryId = scriptData.substringAfter("pc_cid = ").substringBefore(';').trim()
            val contentId = scriptData.substringAfter("pc_id = ").substringBefore(';').trim()

            val urlObj = response.request.url
            val cat = urlObj.pathSegments.getOrNull(0) ?: "beauty"
            val slugFull = urlObj.pathSegments.lastOrNull() ?: ""
            val slug = slugFull.substringBefore(".html").substringBeforeLast("-")

            val ajaxUrlBase = urlObj.newBuilder().apply {
                if (urlObj.pathSize > 0) setPathSegment(0, "ajax_$cat")
                removeAllQueryParameters("ajax")
                removeAllQueryParameters("catid")
                removeAllQueryParameters("conid")
                addQueryParameter("ajax", "1")
                addQueryParameter("catid", categoryId)
                addQueryParameter("conid", contentId)
            }.build()

            val firstImage = document.selectFirst(".pictures img")?.let { img ->
                img.attr("abs:data-original").takeIf { it.isNotEmpty() }
                    ?: img.attr("abs:data-src").takeIf { it.isNotEmpty() }
                    ?: img.attr("abs:data-lazy-src").takeIf { it.isNotEmpty() }
                    ?: img.attr("abs:src")
            }

            if (!firstImage.isNullOrEmpty()) {
                pages.add(Page(0, imageUrl = firstImage))
            } else {
                val pageUrl = ajaxUrlBase.newBuilder()
                    .setPathSegment(ajaxUrlBase.pathSize - 1, "$slug-1.html")
                    .build()
                    .toString()
                pages.add(Page(0, url = pageUrl))
            }

            for (i in 2..numPages) {
                val pageUrl = ajaxUrlBase.newBuilder()
                    .setPathSegment(ajaxUrlBase.pathSize - 1, "$slug-$i.html")
                    .build()
                    .toString()
                pages.add(Page(i - 1, url = pageUrl))
            }
        } else {
            val urlObj = response.request.url
            val slugFull = urlObj.pathSegments.lastOrNull() ?: ""
            val slug = slugFull.substringBefore(".html").substringBeforeLast("-")

            for (i in 1..numPages) {
                val pageUrl = urlObj.newBuilder()
                    .setPathSegment(urlObj.pathSize - 1, "$slug${if (i > 1) "-$i" else ""}.html")
                    .build()
                    .toString()
                pages.add(Page(i - 1, url = pageUrl))
            }
        }

        return pages
    }

    override fun imageUrlParse(response: Response): String {
        val contentType = response.header("Content-Type") ?: ""
        if (contentType.contains("application/json", ignoreCase = true) || response.request.url.queryParameter("ajax") == "1") {
            val pageDto = response.parseAs<Dto>()
            val img = Jsoup.parseBodyFragment(pageDto.pic, baseUrl).selectFirst("img")
            return img?.attr("abs:src") ?: throw Exception("Image not found in AJAX response")
        }

        val document = response.asJsoup()
        val img = document.selectFirst(".pictures img")
        return img?.let { element ->
            element.attr("abs:data-original").takeIf { it.isNotEmpty() }
                ?: element.attr("abs:data-src").takeIf { it.isNotEmpty() }
                ?: element.attr("abs:data-lazy-src").takeIf { it.isNotEmpty() }
                ?: element.attr("abs:src")
        } ?: throw Exception("Image not found in HTML response")
    }

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Header("NOTE: Filter are weird for this extension!"),
        Filter.Separator(),
        TagFilter(),
        ModelFilter(),
        GroupFilter(),
        CategoryFilter(getCategoryFilter(), 0),
        SortFilter(getSortFilter(), 0),
    )
}
