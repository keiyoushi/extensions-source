package eu.kanade.tachiyomi.extension.en.goodgirlsscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Element

class GoodGirlsScan : Madara("Good Girls Scan", "https://goodgirls.moe", "en") {

    override val fetchGenres = false
    override fun popularMangaNextPageSelector() = "body:not(:has(.no-posts))"
    override fun searchMangaSelector() = "article.wp-manga"
    override fun searchMangaNextPageSelector() = "div.paginator .nav-next"
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div.summary-content"
    override val mangaDetailsSelectorDescription = "div.summary-specialfields"
    override fun chapterListSelector() = "li.wp-manga-chapter:not(.vip-permission)"

    private fun madaraLoadMoreRequest(page: Int, metaKey: String): Request {
        val formBody = FormBody.Builder().apply {
            add("action", "madara_load_more")
            add("page", page.toString())
            add("template", "madara-core/content/content-archive")
            add("vars[paged]", "1")
            add("vars[orderby]", "meta_value_num")
            add("vars[template]", "archive")
            add("vars[sidebar]", "right")
            add("vars[post_type]", "wp-manga")
            add("vars[post_status]", "publish")
            add("vars[meta_key]", metaKey)
            add("vars[meta_query][0][paged]", "1")
            add("vars[meta_query][0][orderby]", "meta_value_num")
            add("vars[meta_query][0][template]", "archive")
            add("vars[meta_query][0][sidebar]", "right")
            add("vars[meta_query][0][post_type]", "wp-manga")
            add("vars[meta_query][0][post_status]", "publish")
            add("vars[meta_query][0][meta_key]", metaKey)
            add("vars[meta_query][relation]", "AND")
            add("vars[manga_archives_item_layout]", "default")
        }.build()

        val xhrHeaders = headersBuilder()
            .add("Content-Length", formBody.contentLength().toString())
            .add("Content-Type", formBody.contentType().toString())
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        return POST("$baseUrl/wp-admin/admin-ajax.php", xhrHeaders, formBody)
    }

    override fun popularMangaRequest(page: Int): Request {
        return madaraLoadMoreRequest(page - 1, "_wp_manga_views")
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return madaraLoadMoreRequest(page - 1, "_latest_update")
    }

    override fun searchPage(page: Int): String {
        return if (page > 1) {
            "page/$page/"
        } else {
            ""
        }
    }

    // heavily modified madara theme, throws 5xx errors on any search filter
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/${searchPage(page)}".toHttpUrl().newBuilder().apply {
            addQueryParameter("s", query.trim())
        }.build()

        return GET(url, headers)
    }

    override fun getFilterList() = FilterList()

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        element.select(".entry-title a").let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.text()
        }
        thumbnail_url = element.selectFirst(".post-thumbnail img")?.let(::imageFromElement)
    }
}
