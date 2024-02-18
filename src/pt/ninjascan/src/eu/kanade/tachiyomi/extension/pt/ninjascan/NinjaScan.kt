package eu.kanade.tachiyomi.extension.pt.ninjascan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class NinjaScan : Madara(
    "Ninja Scan",
    "https://ninjascan.site",
    "pt-BR",
    SimpleDateFormat("dd 'de' MMMMM 'de' yyyy", Locale("pt", "BR")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override val useNewChapterEndpoint = true
}
