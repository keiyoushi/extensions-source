package eu.kanade.tachiyomi.extension.ja.jnbooks

import eu.kanade.tachiyomi.multisrc.comiciviewer.ComiciViewerAlt
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.Request
import okhttp3.Response

class JNBooks :
    ComiciViewerAlt(
        "J-N Books",
        "https://comic.j-nbooks.jp",
        "ja",
        "https://comic.j-nbooks.jp/api",
    ) {
    override val supportsLatest = false

    override fun popularMangaRequest(page: Int): Request = latestUpdatesRequest(page)

    override fun popularMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    override fun getFilterOptions(): List<Pair<String, String>> = listOf(
        Pair("更新順", "/series/list/up"),
        Pair("新作順", "/series/list/new"),
        Pair("読み切り", "/category/manga/oneShot"),
        Pair("完結", "/category/manga/complete"),
    )
}
