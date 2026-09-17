package eu.kanade.tachiyomi.extension.ja.hayacomic

import eu.kanade.tachiyomi.multisrc.comiciviewer.ComiciViewer
import eu.kanade.tachiyomi.network.GET
import keiyoushi.annotation.Source
import okhttp3.Request

@Source
abstract class HayaComic : ComiciViewer() {
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/series/list/up/$page", headers)
}
