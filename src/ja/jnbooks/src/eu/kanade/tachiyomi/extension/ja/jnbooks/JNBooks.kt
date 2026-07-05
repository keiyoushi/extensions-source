package eu.kanade.tachiyomi.extension.ja.jnbooks

import eu.kanade.tachiyomi.multisrc.comiciviewer.ComiciViewer
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.MangasPage
import keiyoushi.annotation.Source
import okhttp3.Request
import okhttp3.Response

@Source
abstract class JNBooks : ComiciViewer() {
    override val supportsLatest = false

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/category/manga/$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    override fun getFilterOptions(): List<Pair<String, String>> = listOf(
        Pair("更新順", "/series/list/up"),
        Pair("新作順", "/series/list/new"),
        Pair("読み切り", "/category/manga/oneShot"),
        Pair("完結", "/category/manga/complete"),
    )
}
