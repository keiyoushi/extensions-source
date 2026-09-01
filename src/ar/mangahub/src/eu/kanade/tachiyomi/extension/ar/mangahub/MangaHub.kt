package eu.kanade.tachiyomi.extension.ar.mangahub

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

@Source
abstract class MangaHub : ZeistManga() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    // Missing popular
    override val supportsLatest = false

    override val mangaDetailsSelector = ".grid.gap-5.gta-series"
    override val mangaDetailsSelectorInfo = "dt"

    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        document
            .selectFirst("dt:contains(الإشارات) + dd")
            ?.text()
            ?.split(",")
            ?.filterNot(String::isEmpty)
            ?.joinToString()
            ?.also { genre = it }
    }

    override val pageListSelector = "article#reader .separator, div.image-container"
}
