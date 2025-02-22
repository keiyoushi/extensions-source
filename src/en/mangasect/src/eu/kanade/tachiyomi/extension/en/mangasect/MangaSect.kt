package eu.kanade.tachiyomi.extension.en.mangasect
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.liliana.Liliana
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class MangaSect : Liliana(
    "Manga Sect",
    "https://mangasect.net",
    "en",
    usesPostSearch = true,
) {
    override val client = super.client.newBuilder()
        .rateLimit(1)
        .build()
}
