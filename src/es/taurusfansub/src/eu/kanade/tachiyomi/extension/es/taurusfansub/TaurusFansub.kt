package eu.kanade.tachiyomi.extension.es.taurusfansub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class TaurusFansub : Madara(
    "Taurus Fansub",
    "https://taurusmanga.com",
    "es",
    dateFormat = SimpleDateFormat("dd/MM/yyy", Locale.ROOT),
) {
    override val client = super.client.newBuilder()
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()

    override val useNewChapterEndpoint = true
    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val mangaDetailsSelectorDescription = "div.tab-summary > div.tab-content > div#tab-reducir > div.contenedor"
}
