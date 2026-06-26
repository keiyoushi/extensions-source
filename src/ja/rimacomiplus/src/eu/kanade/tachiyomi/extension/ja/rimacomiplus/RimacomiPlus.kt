package eu.kanade.tachiyomi.extension.ja.rimacomiplus

import eu.kanade.tachiyomi.multisrc.comiciviewer.ComiciViewerAlt
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request

class RimacomiPlus :
    ComiciViewerAlt(
        "RimacomiPlus",
        "https://rimacomiplus.jp",
        "ja",
        "https://rimacomiplus.jp/api",
    ) {
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/category/manga/$page", headers)

    override fun getFilterOptions(): List<Pair<String, String>> = listOf(
        Pair("ランキング", "/ranking/manga"),
        Pair("更新順", "/series/list/up"),
        Pair("新作順", "/series/list/new"),
        Pair("読み切り", "/category/manga/oneShot"),
        Pair("完結", "/category/manga/complete"),
        Pair("連載", "/category/manga"),
    )
}
