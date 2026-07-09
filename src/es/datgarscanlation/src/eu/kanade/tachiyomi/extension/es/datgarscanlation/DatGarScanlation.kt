package eu.kanade.tachiyomi.extension.es.datgarscanlation

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit

@Source
abstract class DatGarScanlation : ZeistManga() {
    override val useNewChapterFeed = true
    override val hasFilters = true
    override val hasLanguageFilter = false

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
