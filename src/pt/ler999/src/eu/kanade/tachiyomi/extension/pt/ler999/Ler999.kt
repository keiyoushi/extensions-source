package eu.kanade.tachiyomi.extension.pt.ler999

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.util.concurrent.TimeUnit

class Ler999 : ZeistManga("Ler 999", "https://ler999.blogspot.com", "pt-BR") {
    override val client = super.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()
}
