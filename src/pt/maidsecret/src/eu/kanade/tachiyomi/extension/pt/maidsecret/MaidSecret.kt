package eu.kanade.tachiyomi.extension.pt.maidsecret

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class MaidSecret : Madara(
    "Maid Secret",
    "https://maidsecret.com",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
) {
    override val useNewChapterEndpoint = true

    override val client = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()
}
