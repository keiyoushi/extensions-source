package eu.kanade.tachiyomi.extension.ar.galaxymanga

import eu.kanade.tachiyomi.multisrc.flixscans.FlixScans
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request

class GalaxyManga : FlixScans("جالاكسي مانجا", "https://flixscans.com", "ar") {
    override val versionId = 2

    override fun popularMangaRequest(page: Int): Request {
        return GET("$apiUrl/webtoon/pages/home/action", headers)
    }
}
