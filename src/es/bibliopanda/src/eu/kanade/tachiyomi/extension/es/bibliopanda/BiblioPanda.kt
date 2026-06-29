package eu.kanade.tachiyomi.extension.es.bibliopanda

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class BiblioPanda :
    Madara(
        "Biblio Panda",
        "https://bibliopanda.com",
        "es",
        SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
    ) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Never
}
