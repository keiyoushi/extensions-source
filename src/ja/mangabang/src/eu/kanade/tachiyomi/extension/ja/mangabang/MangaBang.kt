package eu.kanade.tachiyomi.extension.ja.mangabang

import eu.kanade.tachiyomi.multisrc.comiciviewer.ComiciViewer
import eu.kanade.tachiyomi.network.GET
import keiyoushi.annotation.Source
import okhttp3.Request

@Source
abstract class MangaBang : ComiciViewer() {
    override val rankingFromNextJs = false

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/category/manga/$page", headers)

    override fun getFilterOptions(): List<Pair<String, String>> = listOf(
        Pair("ランキング", "/ranking/manga"),
        Pair("更新順", "/series/list/up"),
        Pair("新作順", "/series/list/new"),
        Pair("完結", "/category/manga/complete"),
    )
}
