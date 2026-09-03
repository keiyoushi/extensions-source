package eu.kanade.tachiyomi.extension.ja.piacomic

import eu.kanade.tachiyomi.multisrc.comiciviewer.ComiciViewer
import eu.kanade.tachiyomi.network.GET
import keiyoushi.annotation.Source
import okhttp3.Request

@Source
abstract class PiaComic : ComiciViewer() {
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/series/list/up/$page", headers)
}
