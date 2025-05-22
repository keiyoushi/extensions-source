package eu.kanade.tachiyomi.extension.pt.ler999

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.network.rateLimit
import kotlin.time.Duration.Companion.seconds

class Ler999 : ZeistManga("Ler 999", "https://ler999.blogspot.com", "pt-BR") {
    override val client = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()
}
