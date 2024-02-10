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

    class TypeFilter : UriPartFilter(
        "タイプ",
        "type",
        arrayOf(
            Pair("全て", "all"),
            Pair("Raw Manga", "Raw Manga"),
            Pair("BLコミック", "BLコミック"),
            Pair("TLコミック", "TLコミック"),
            Pair("オトナコミック", "オトナコミック"),
            Pair("女性マンガ", "女性マンガ"),
            Pair("少女マンガ", "少女マンガ"),
            Pair("少年マンガ", "少年マンガ"),
            Pair("青年マンガ", "青年マンガ"),
        ),
    )

    class StatusFilter : UriPartFilter(
        "地位",
        "status",
        arrayOf(
            Pair("全て", "all"),
            Pair("Publishing", "Publishing"),
            Pair("Finished", "Finished"),
        ),
    )

    class LanguageFilter : UriPartFilter(
        "言語",
        "language",
        arrayOf(
            Pair("全て", "all"),
            Pair("Japanese", "ja"),
            Pair("English", "en"),
        ),
    )

    class GenreFilter : UriMultiSelectFilter(
        "ジャンル",
        "genre[]",
        arrayOf(
            Pair("アクション", "55"),
            Pair("エッチ", "15706"),
            Pair("コメディ", "91"),
            Pair("ドラマ", "56"),
            Pair("ハーレム", "20"),
            Pair("ファンタジー", "1"),
            Pair("冒険", "54"),
            Pair("悪魔", "6820"),
            Pair("武道", "1064"),
            Pair("歴史的", "9600"),
            Pair("警察・特殊部隊", "6089"),
            Pair("車･バイク", "4329"),
            Pair("音楽", "473"),
            Pair("魔法", "1416"),
        ),
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
