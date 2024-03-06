package eu.kanade.tachiyomi.extension.es.datgarscanlation

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.util.concurrent.TimeUnit

class DatGarScanlation : ZeistManga(
    "DatGarScanlation",
    "https://datgarscanlation.blogspot.com",
    "es",
) {
    override val useNewChapterFeed = true
    override val hasFilters = true
    override val hasLanguageFilter = false

    override val client = super.client.newBuilder()
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()
}
