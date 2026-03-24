package eu.kanade.tachiyomi.extension.ja.ichicomi

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.firstInstance
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Element

class Ichicomi :
    GigaViewer(
        "Ichicomi",
        "https://ichicomi.com",
        "ja",
    ) {
    override val supportsLatest = false

    override val popularMangaSelector: String = "div[class^=Series_series__]"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("a")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = link.selectFirst("h4[class^=Series_title__]")!!.text()
        thumbnail_url = link.selectFirst("img[class^=Series_thumbnail__]")?.absUrl("src")
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = "$baseUrl/$searchPathSegment".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .build()
            return GET(url, headers)
        }

        val filter = filters.firstInstance<CollectionFilter>()
        val url = "$baseUrl/${filter.getPath()}"
        return GET(url, headers)
    }

    override val searchMangaSelector: String = "li[class^=SearchResultItem_li__]"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("a")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = element.selectFirst("p[class^=SearchResultItem_series_title__]")!!.text()
        thumbnail_url = link.selectFirst("img")?.absUrl("src")
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
