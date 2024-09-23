package eu.kanade.tachiyomi.extension.id.cosmicscansid

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class CosmicScansID : MangaThemesia("CosmicScans.id", "https://cosmic1.co", "id", "/semua-komik") {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(20, 4, TimeUnit.SECONDS)
        .build()

    override val hasProjectPage = true
    override val projectPageString = "/semua-komik"

    override fun latestUpdatesRequest(page: Int) = GET(baseUrl + if (page > 1) "/page/$page" else "", headers)

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("page/$page/")
            .addQueryParameter("s", query)

        filters.forEach { filter ->
            when (filter) {
                // if site has project page, default value "hasProjectPage" = false
                is ProjectFilter -> {
                    if (filter.selectedValue() == "project-filter-on") {
                        url.setPathSegment(0, projectPageString.substring(1))
                    }
                }
                else -> { /* Do Nothing */ }
            }
        }
        return GET(url.build())
    }

    override fun searchMangaSelector() = ".bixbox:not(.hothome):has(.hpage) .utao .uta .imgu, .bixbox:not(.hothome) .listupd .bs .bsx"

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>(
            Filter.Separator(),
            Filter.Header("$name Project List page"),
            ProjectFilter(intl["project_filter_title"], projectFilterOptions),
            OrderByFilter(intl["order_by_filter_title"], orderByFilterOptions),
        )
        return FilterList(filters)
    }

    // manga details
    override val seriesDescriptionSelector = ".entry-content[itemprop=description] :not(a,p:has(a))"

    // pages
    override val pageSelector = "div#readerarea img:not(noscript img)"
}
