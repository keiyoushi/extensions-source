package eu.kanade.tachiyomi.extension.es.novatoscans

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.network.rateLimit

class NovatoScans : ZeistManga(
    "Novato Scans",
    "https://www.novatoscans.top",
    "es",
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val pageListSelector = "div.check-box"
}
