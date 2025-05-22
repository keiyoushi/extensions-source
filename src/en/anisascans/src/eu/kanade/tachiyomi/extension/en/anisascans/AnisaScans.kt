package eu.kanade.tachiyomi.extension.en.anisascans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit

class AnisaScans : Madara(
    "Anisa Scans",
    "https://anisascans.in",
    "en",
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val useNewChapterEndpoint = true
}
