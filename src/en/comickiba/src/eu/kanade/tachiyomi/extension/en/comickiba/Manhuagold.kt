package eu.kanade.tachiyomi.extension.en.comickiba
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.liliana.Liliana
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class Manhuagold : Liliana(
    "Manhuagold",
    "https://manhuagold.top",
    "en",
    usesPostSearch = true,
) {
    // MangaReader -> Liliana
    override val versionId = 2

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
