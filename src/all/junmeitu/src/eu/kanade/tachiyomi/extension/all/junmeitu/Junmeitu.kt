package eu.kanade.tachiyomi.extension.all.junmeitu

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import org.jsoup.Jsoup
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Junmeitu : KeiSource() {

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList("$baseUrl/beauty/index-$page.html")

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList("$baseUrl/beauty/hot-$page.html")

    private suspend fun parseMangaList(url: String): MangasPage {
        val document = client.get(url).asJsoup()
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

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val tagFilter = filters.firstInstanceOrNull<TagFilter>()
        val modelFilter = filters.firstInstanceOrNull<ModelFilter>()
        val groupFilter = filters.firstInstanceOrNull<GroupFilter>()
        val categoryFilter = filters.firstInstanceOrNull<CategoryFilter>()
        val sortFilter = filters.firstInstanceOrNull<SortFilter>()

        val url = when {
            query.isNotEmpty() -> "$baseUrl/search/$query-$page.html"
            tagFilter != null && tagFilter.state.isNotEmpty() -> "$baseUrl/tags/${tagFilter.state}-${categoryFilter?.selected ?: "6"}-$page.html"
            modelFilter != null && modelFilter.state.isNotEmpty() -> "$baseUrl/model/${modelFilter.state}-$page.html"
            groupFilter != null && groupFilter.state.isNotEmpty() -> "$baseUrl/xzjg/${groupFilter.state}-$page.html"
            categoryFilter != null && categoryFilter.state != 0 -> "$baseUrl/${categoryFilter.slug}/${sortFilter?.selected ?: "index"}-$page.html"
            sortFilter != null && sortFilter.state != 0 -> "$baseUrl/${categoryFilter?.slug ?: "beauty"}/${sortFilter.selected}-$page.html"
            else -> "$baseUrl/beauty/index-$page.html"
        }

        return parseMangaList(url)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get(getMangaUrl(manga))
        val requestUrl = response.request.url.toString()
        val document = response.asJsoup()

        manga.apply {
            title = document.selectFirst(".news-title, .title")?.text() ?: title
            description = buildString {
                append(document.select(".news-info, .picture-details").joinToString(" ") { it.text() })
                append("\n")
                append(document.select(".introduce").text())
            }
            genre = document.select(".relation_tags > a").joinToString(", ") { it.text() }
            status = SManga.COMPLETED
        }

        val chapter = SChapter.create().apply {
            val urlElement = document.selectFirst(".position a:last-child")
            val href = urlElement?.attr("abs:href")
            setUrlWithoutDomain(if (!href.isNullOrEmpty()) href else requestUrl)
            name = "Gallery"

            val dateText = document.select(".base-info span:contains(日期)").text().substringAfter("日期:").trim()
            date_upload = dateFormat.tryParseDate(dateText)
        }

        return SMangaUpdate(manga, listOf(chapter))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(getChapterUrl(chapter))
        val urlObj = response.request.url
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

    override suspend fun getImageUrl(page: Page): String {
        val response = client.get(page.url)
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

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
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
