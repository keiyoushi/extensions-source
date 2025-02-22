package eu.kanade.tachiyomi.extension.pt.tyrantscans

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request

class TyrantScans : ZeistManga("Tyrant Scans", "https://www.tyrantscans.com", "pt-BR") {

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override val popularMangaSelector = "#PopularPosts3 article"
    override val popularMangaSelectorTitle = "h3 a"
    override val popularMangaSelectorUrl = popularMangaSelectorTitle
}
