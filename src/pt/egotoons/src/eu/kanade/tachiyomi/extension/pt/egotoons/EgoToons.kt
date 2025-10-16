package eu.kanade.tachiyomi.extension.pt.egotoons

import eu.kanade.tachiyomi.multisrc.zerotheme.ZeroTheme
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class EgoToons : ZeroTheme(
    "Ego Toons",
    "https://egotoons.com",
    "pt-BR",
) {
    override val versionId = 2

    override val supportsLatest = false

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override val imageLocation = "/image-db"

    override fun fetchPopularManga(page: Int) = fetchLatestUpdates(page)
}
