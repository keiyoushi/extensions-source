package eu.kanade.tachiyomi.extension.es.nekoscans

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import java.util.concurrent.TimeUnit

class NekoScans : ZeistManga(
    "NekoScans",
    "https://nekoscanlationlector.blogspot.com",
    "es",
) {
    // Theme changed from MangaThemesia to ZeistManga
    override val versionId = 2

    override val client = super.client.newBuilder()
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()

    override val excludedCategories = listOf("Anime", "Novel")

    override fun popularMangaRequest(page: Int) = latestUpdatesRequest(page)
    override fun popularMangaParse(response: Response) = latestUpdatesParse(response)
    override val supportsLatest = false

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        document.selectFirst("header[itemprop=mainEntity]")!!.let { element ->
            title = element.selectFirst("h1[itemprop=name]")!!.text()
            thumbnail_url = element.selectFirst("img[itemprop=image]")!!.attr("src")
            status = parseStatus(element.selectFirst("span[data-status]")!!.text())
            genre = element.select("dl:has(dt:contains(Genre)) > dd > a[rel=tag]").joinToString { it.text() }
        }
        description = document.selectFirst("#synopsis, #sinop")!!.text()
        document.selectFirst("div#extra-info")?.let { element ->
            author = element.selectFirst("dl:has(dt:contains(Autor)) > dd")!!.text()
            artist = element.selectFirst("dl:has(dt:contains(Artista)) > dd")!!.text()
        }
    }

    override val pageListSelector = "div#readarea img"
}
