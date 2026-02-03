package eu.kanade.tachiyomi.extension.ja.ichicomi

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Element

class Ichicomi :
    GigaViewer(
        "Ichicomi",
        "https://ichicomi.com",
        "ja",
        "https://cdn-img.ichicomi.com",
        isPaginated = true,
    ) {
    override val supportsLatest = false

    override val publisher: String = "一迅社"

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(::imageIntercept)
        .build()

    override fun popularMangaSelector(): String = "div[class^=Series_series__]"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("a")!!
        setUrlWithoutDomain(link.attr("href"))
        title = link.selectFirst("h4[class^=Series_title__]")!!.text()
        thumbnail_url = link.selectFirst("img[class^=Series_thumbnail__]")?.attr("src")
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
            return GET(url.build(), headers)
        }

        val filter = filters.firstOrNull() as? CollectionFilter
        if (filter != null && filter.state != 0) {
            val path = filter.getPath()
            if (path.isNotEmpty()) {
                return GET("$baseUrl/$path", headers)
            }
        }
        return popularMangaRequest(page)
    }

    override fun searchMangaSelector(): String = "li[class^=SearchResultItem_li__]"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("a")!!
        setUrlWithoutDomain(link.attr("href"))
        title = element.selectFirst("p[class^=SearchResultItem_series_title__]")!!.text()
        thumbnail_url = link.selectFirst("img")?.attr("src")
    }

    private class CollectionFilter(filters: List<Pair<String, String>>) : Filter.Select<String>("フィルター", filters.map { it.first }.toTypedArray()) {
        private val paths = filters.map { it.second }
        fun getPath(): String = paths[state]
    }

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Pair<String, String>>().apply {
            add(Pair("すべて", ""))
            addAll(getCollections().map { Pair(it.name, "series/${it.path}") })
            addAll(getGenres().map { Pair(it.first, "genres/${it.second}") })
        }
        return FilterList(CollectionFilter(filters))
    }

    private fun getGenres(): List<Pair<String, String>> = listOf(
        Pair("恋愛", "romance"),
        Pair("ラブコメ", "romantic-comedy"),
        Pair("コメディ・ギャグ", "comedy"),
        Pair("ホラー", "horror"),
        Pair("サスペンス", "suspense"),
        Pair("アクション・バトル", "action"),
        Pair("歴史・時代", "history"),
        Pair("日常", "nichijo"),
        Pair("ヒューマンドラマ", "drama"),
        Pair("異世界・転生", "isekai"),
        Pair("ファンタジー", "fantasy"),
        Pair("百合", "yuri"),
        Pair("BL", "bl"),
        Pair("TL", "tl"),
        Pair("学園・青春", "school"),
        Pair("お仕事", "work"),
        Pair("メディア化", "media"),
        Pair("新人・読切", "oneshot"),
        Pair("完結", "finished"),
        Pair("オリジナル", "original"),
    )

    override fun getCollections(): List<Collection> = listOf(
        Collection("echo", "echo"),
        Collection("gateau", "gateau"),
        Collection("カラフルハピネス", "colorful_happiness"),
        Collection("REX", "rex"),
        Collection("HOWL", "howl"),
        Collection("POOL", "pool"),
        Collection("百合姫", "yurihime"),
        Collection("LAKE", "lake"),
        Collection("ZERO-SUM", "zerosum"),
        Collection("ぱれっと", "palette"),
        Collection("ベビードール", "babydoll"),
        Collection("一迅プラス", "ichijin-plus"),
    )
}
