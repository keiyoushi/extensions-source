package eu.kanade.tachiyomi.extension.pt.gekkouscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class GekkouScans : Madara(
    "Gekkou Scans",
    "https://gekkou.space",
    "pt-BR",
    SimpleDateFormat("dd 'de' MMM 'de' yyyy", Locale("pt", "BR")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Never
}
