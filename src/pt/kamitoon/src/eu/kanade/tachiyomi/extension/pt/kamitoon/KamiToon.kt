package eu.kanade.tachiyomi.extension.pt.kamitoon

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class KamiToon :
    Madara(
        "Kami Toon",
        "https://kamitoon.com.br",
        "pt-BR",
        SimpleDateFormat("dd 'de' MMM 'de' yyyy", Locale("pt", "BR")),
    ) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Never
}
