package eu.kanade.tachiyomi.extension.ar.mangahub

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Response
import org.jsoup.Jsoup

class MangaHub : ZeistManga(
    "MangaHub",
    "https://www.mangaxhentai.com",
    "ar",
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    // Missing popular
    override val supportsLatest = false
    override fun popularMangaRequest(page: Int) = latestUpdatesRequest(page)
    override fun popularMangaParse(response: Response) = latestUpdatesParse(response)

    override val mangaDetailsSelector = ".grid.gap-5.gta-series"
    override val mangaDetailsSelectorInfo = "dt"

    override fun mangaDetailsParse(response: Response): SManga {
        val document = Jsoup.parse(
            response.peekBody(Long.MAX_VALUE).string(),
            response.request.url.toString(),
        )
        return super.mangaDetailsParse(response).apply {
            document
                .selectFirst("dt:contains(الإشارات) + dd")
                ?.text()
                ?.split(",")
                ?.filterNot(String::isEmpty)
                ?.joinToString()
                ?.also { genre = it }
        }
    }

    override val pageListSelector = "article#reader .separator, div.image-container"
}
