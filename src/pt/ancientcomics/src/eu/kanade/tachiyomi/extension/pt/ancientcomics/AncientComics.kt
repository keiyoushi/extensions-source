package eu.kanade.tachiyomi.extension.pt.ancientcomics

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class AncientComics : Madara(
    "Ancient Comics",
    "https://ancientcomics.com.br",
    "pt-BR",
    dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
) {
    override val versionId: Int = 2

    override val useNewChapterEndpoint = true

    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
