package eu.kanade.tachiyomi.extension.pt.xsscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class XsScan : Madara(
    "Xs Scan",
    "https://xsscan.xyz",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val useNewChapterEndpoint = true
}
