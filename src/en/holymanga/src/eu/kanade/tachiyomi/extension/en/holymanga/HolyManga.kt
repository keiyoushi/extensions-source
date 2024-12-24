package eu.kanade.tachiyomi.extension.en.holymanga

import eu.kanade.tachiyomi.multisrc.zbulu.Zbulu
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request

class HolyManga : Zbulu(
    "HolyManga",
    "https://w34.holymanga.net",
    "en",
) {
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/popular-manga/page-$page/", headers)
    }
}
