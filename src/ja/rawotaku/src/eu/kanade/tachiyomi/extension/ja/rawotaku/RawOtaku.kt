package eu.kanade.tachiyomi.extension.ja.rawotaku

import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl

class RawOtaku : MangaReader(
    "Raw Otaku",
    "https://rawotaku.com",
    "ja",
) {

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun addPage(page: Int, builder: HttpUrl.Builder) {
        builder.addQueryParameter("p", page.toString())
    }

    // =============================== Search ===============================

    override val searchPathSegment = ""
    override val searchKeyword = "q"

    // ============================== Chapters ==============================

    override val chapterIdSelect = "ja-chaps"

    // =============================== Pages ================================

    override fun getAjaxUrl(id: String): String {
        return "$baseUrl/json/chapter?mode=vertical&id=$id"
    }

    // =============================== Filters ==============================

    override fun getFilterList() = FilterList(
        Note,
        Filter.Separator(),
        TypeFilter(),
        StatusFilter(),
        LanguageFilter(),
        getSortFilter(),
        GenreFilter(),
    )

    override fun sortFilterValues(): Array<Pair<String, String>> {
        return arrayOf(
            Pair("デフォルト", "default"),
            Pair("最新の更新", "latest-updated"),
            Pair("最も見られました", "most-viewed"),
            Pair("Title [A-Z]", "title-az"),
            Pair("Title [Z-A]", "title-za"),
        )
    }
}
