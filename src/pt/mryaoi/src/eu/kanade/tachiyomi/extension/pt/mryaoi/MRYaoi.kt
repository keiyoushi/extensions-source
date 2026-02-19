package eu.kanade.tachiyomi.extension.pt.mryaoi

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class MRYaoi :
    Madara(
        "MR Yaoi",
        "https://mrtenzus.com",
        "pt-BR",
        SimpleDateFormat("MM/dd/yyyy", Locale("pt", "BR")),
    ) {

    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val useNewChapterEndpoint = true
}
