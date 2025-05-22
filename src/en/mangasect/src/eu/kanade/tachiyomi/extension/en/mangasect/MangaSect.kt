package eu.kanade.tachiyomi.extension.en.mangasect

import eu.kanade.tachiyomi.multisrc.liliana.Liliana
import keiyoushi.network.rateLimit

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
