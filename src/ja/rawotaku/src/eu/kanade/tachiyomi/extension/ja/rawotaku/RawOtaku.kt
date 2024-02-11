package eu.kanade.tachiyomi.extension.ja.rawotaku

import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Request
import org.jsoup.nodes.Element
import java.net.URLEncoder

class RawOtaku : MangaReader("Raw Otaku", "https://rawotaku.com", "ja") {

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override val pageQueryParameter = "p"
    override val searchKeyword = "q"

    // ============================== Chapters ==============================

    override fun chapterFromElement(element: Element) = super.chapterFromElement(element).apply {
        val id = element.attr("data-id")
        url = "$url#$id"
    }

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request {
        val id = chapter.url.substringAfterLast("#")

        val ajaxHeaders = super.headersBuilder().apply {
            add("Accept", "application/json, text/javascript, */*; q=0.01")
            add("Referer", URLEncoder.encode(baseUrl + chapter.url.substringBeforeLast("#"), "utf-8"))
            add("X-Requested-With", "XMLHttpRequest")
        }.build()

        val ajaxUrl = "$baseUrl/json/chapter?mode=vertical&id=$id"

        return GET(ajaxUrl, ajaxHeaders)
    }

    // =============================== Filters ==============================

    override val sortFilterValues = arrayOf(
        Pair("デフォルト", "default"),
        Pair("最新の更新", "latest-updated"),
        Pair("最も見られました", "most-viewed"),
        Pair("Title [A-Z]", "title-az"),
        Pair("Title [Z-A]", "title-za"),
    )

    override fun getFilterList() = FilterList(
        Note,
        Filter.Separator(),
        TypeFilter(),
        StatusFilter(),
        LanguageFilter(),
        getSortFilter(),
        GenreFilter(),
    )
}
