package eu.kanade.tachiyomi.extension.ja.rimacomiplus

import eu.kanade.tachiyomi.multisrc.comiciviewer.ComiciViewer
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

class RimacomiPlus : ComiciViewer(
    "RimacomiPlus",
    "https://rimacomiplus.jp",
    "ja",
) {
    override fun chapterListRequest(manga: SManga): Request {
        val newClient = super.client.newBuilder()
            .followRedirects(false)
            .build()

        val temp = newClient.newCall(GET(baseUrl + manga.url, headers)).execute()
        val redirect = temp.header("Location")
        val url = redirect.toString().toHttpUrl().newBuilder()
            .setQueryParameter("s", "1")
            .addPathSegment("list")
            .build()
        return GET(url, headers)
    }

    override fun getFilterOptions(): List<Pair<String, String>> = listOf(
        Pair("ランキング", "/ranking/manga"),
        Pair("読み切り", "/category/manga?type=読み切り"),
        Pair("完結", "/category/manga?type=完結"),
        Pair("連載", "/category/manga?type=連載中"),
    )
}
