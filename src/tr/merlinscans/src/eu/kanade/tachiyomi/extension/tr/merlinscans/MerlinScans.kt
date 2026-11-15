package eu.kanade.tachiyomi.extension.tr.merlinscans

import eu.kanade.tachiyomi.multisrc.initmanga.InitManga
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

@Source
abstract class MerlinScans : InitManga() {

    override val mangaUrlDirectory = "manga"

    override val popularUrlSlug = "manga-ranking"

    override val latestUrlSlug = "manga"

    // External image hosts (e.g. ragnarscans.net) reset connections when Referer is merlinscans.org
    override fun imageRequest(page: Page): Request {
        val request = super.imageRequest(page)
        if (request.url.host != baseUrl.toHttpUrl().host) {
            return request.newBuilder()
                .removeHeader("Referer")
                .build()
        }
        return request
    }
}
