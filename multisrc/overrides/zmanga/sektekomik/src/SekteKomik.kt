package eu.kanade.tachiyomi.extension.id.sektekomik

import eu.kanade.tachiyomi.multisrc.zmanga.ZManga
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

class SekteKomik : ZManga("Sekte Komik", "https://sektekomik.com", "id") {
    // Formerly "Sekte Komik (WP Manga Stream)"
    override val id = 7866629035053218469

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .rateLimit(3)
        .build()

    // popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl")
    }

    override fun popularMangaSelector() = "div.flexbox-item"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.select("a").attr("href"))
            title = element.select("a").attr("title")
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun popularMangaNextPageSelector() = "Not used"

    // latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/page/$page")
    }

    override fun latestUpdatesSelector() = "div.flexbox4-item"

    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.select("div.flexbox4-content a").attr("href"))
            title = element.select("div.flexbox4-side .title").first()!!.text()
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun latestUpdatesNextPageSelector() = "div.pagination .next"

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url = "$baseUrl/${pagePathSegment(page)}".toHttpUrlOrNull()!!.newBuilder()
        url.addQueryParameter("s", query)
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                // if site has project page, default value "hasProjectPage" = false
                is ProjectFilter -> {
                    if (filter.toUriPart() == "project-filter-on") {
                        url = "$baseUrl$projectPageString/${pagePathSegment(page)}".toHttpUrlOrNull()!!.newBuilder()
                    }
                }
                else -> {}
            }
        }
        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = "div.flexbox2-item"

    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.select("div.flexbox2-content a").attr("href"))
            title = element.select("div.flexbox2-title > span").first()!!.text()
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    // filter
    override val hasProjectPage = true

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>(
            Filter.Separator(),
            Filter.Header("NOTE: cant be used with search!"),
            Filter.Header("$name Project List page"),
            ProjectFilter(),
        )
        return FilterList(filters)
    }
}
