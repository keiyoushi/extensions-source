package eu.kanade.tachiyomi.extension.id.komikplay

import eu.kanade.tachiyomi.multisrc.zmanga.ZManga
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class KomikPlay : ZManga("KomikPlay", "https://komikplay.com", "id", SimpleDateFormat("d MMM yyyy", Locale.US)) {

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/${pagePathSegment(page)}/?s")
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/${pagePathSegment(page)}")
    }

    override fun latestUpdatesSelector() = "h2:contains(New) + .flexbox3 .flexbox3-item"

    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.select("div.flexbox3-content a").attr("href"))
            title = element.select("div.flexbox3-content a").attr("title")
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url = "$baseUrl/${pagePathSegment(page)}".toHttpUrlOrNull()!!.newBuilder()
        url.addQueryParameter("s", query)
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                // if site has project page, default value "hasProjectPage" = false
                is ProjectFilter -> {
                    if (filter.toUriPart() == "project-filter-on") {
                        url = "$baseUrl$projectPageString/page/$page".toHttpUrlOrNull()!!.newBuilder()
                    }
                }
                else -> {}
            }
        }
        return GET(url.toString(), headers)
    }

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: cant be used with other filter!"),
        Filter.Header("$name Project List page"),
        ProjectFilter(),
    )

    override val hasProjectPage = true
}
