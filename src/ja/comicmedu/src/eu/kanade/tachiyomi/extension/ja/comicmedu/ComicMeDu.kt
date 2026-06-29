package eu.kanade.tachiyomi.extension.ja.comicmedu

import eu.kanade.tachiyomi.multisrc.comiciviewer.ComiciViewerAlt
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request

class ComicMeDu :
    ComiciViewerAlt(
        "G-Comi",
        "https://g-comi.jp",
        "ja",
        "https://g-comi.jp/api",
    ) {
    override val id = 7310112963091407823

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/category/manga/$page", headers)
}
