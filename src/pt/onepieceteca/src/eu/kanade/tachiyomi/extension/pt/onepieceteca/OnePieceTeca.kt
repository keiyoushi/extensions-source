package eu.kanade.tachiyomi.extension.pt.onepieceteca

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class OnePieceTeca : Madara(
    "One Piece TECA",
    "https://onepieceteca.com",
    "pt-BR",
    SimpleDateFormat("MMMM dd, yyyy", Locale("pt", "BR")),
) {
    override val supportsLatest = false

    override val useNewChapterEndpoint = true

    override val mangaSubString = "ler-online"

    override val client = network.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()
}
