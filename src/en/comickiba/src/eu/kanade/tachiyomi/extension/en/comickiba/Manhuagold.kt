package eu.kanade.tachiyomi.extension.en.comickiba

import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response

class Manhuagold : MangaReader(
    "Manhuagold",
    "https://manhuagold.top",
    "en",
    "views",
) {

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // =============================== Pages ================================

    override val pageListParseSelector = "div"

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val script = document.selectFirst("script:containsData(const CHAPTER_ID)")!!.data()
        val id = script.substringAfter("const CHAPTER_ID = ").substringBefore(";")

        val ajaxHeaders = super.headersBuilder().apply {
            add("Accept", "application/json, text/javascript, */*; q=0.01")
            add("Referer", response.request.url.toString())
            add("X-Requested-With", "XMLHttpRequest")
        }.build()

        val ajaxUrl = "$baseUrl/ajax/image/list/chap/$id"

        val ajaxResponse = client.newCall(GET(ajaxUrl, ajaxHeaders)).execute()
        return super.pageListParse(ajaxResponse)
    }

    // =============================== Filters ==============================

    override val sortFilterValues = arrayOf(
        Pair("Default", "default"),
        Pair("Latest Updated", "latest-updated"),
        Pair("Most Viewed", "views"),
        Pair("Most Viewed Month", "views_month"),
        Pair("Most Viewed Week", "views_week"),
        Pair("Most Viewed Day", "views_day"),
        Pair("Score", "score"),
        Pair("Name A-Z", "az"),
        Pair("Name Z-A", "za"),
        Pair("The highest chapter count", "chapters"),
        Pair("Newest", "new"),
        Pair("Oldest", "old"),
    )

    override fun getFilterList() = FilterList(
        Note,
        Filter.Separator(),
        StatusFilter(),
        getSortFilter(),
        GenreFilter(),
    )
}
