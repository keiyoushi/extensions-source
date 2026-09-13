package eu.kanade.tachiyomi.extension.ja.mangazegra

import eu.kanade.tachiyomi.multisrc.comiciviewer.ComiciViewer
import eu.kanade.tachiyomi.network.GET
import keiyoushi.annotation.Source
import okhttp3.Request

@Source
abstract class MangaZegra : ComiciViewer() {
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/category/manga/$page", headers)
}
