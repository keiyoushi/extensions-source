package eu.kanade.tachiyomi.extension.pt.nirvanascan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class NirvanaScan : Madara(
    "Nirvana Scan",
    "https://nirvanascan.com",
    "pt-BR",
    SimpleDateFormat("MMM dd, yyyy", Locale("pt", "BR")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val useNewChapterEndpoint = true
}
