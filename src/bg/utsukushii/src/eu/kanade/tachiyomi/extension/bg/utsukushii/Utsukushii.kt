package eu.kanade.tachiyomi.extension.bg.utsukushii

import eu.kanade.tachiyomi.multisrc.mmrcms.MMRCMS
import eu.kanade.tachiyomi.network.GET
import keiyoushi.annotation.Source
import okhttp3.Request

@Source
abstract class Utsukushii : MMRCMS() {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga-list", headers)
}
