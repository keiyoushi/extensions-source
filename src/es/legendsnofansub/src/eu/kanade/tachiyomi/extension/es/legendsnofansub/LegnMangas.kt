package eu.kanade.tachiyomi.extension.es.legendsnofansub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale

class LegnMangas : Madara(
    "LegnMangas",
    "https://legnmangas.com",
    "es",
    SimpleDateFormat("dd/MM/yyyy", Locale("es")),
) {
    override val id = 9078720153732517844

    override val client = super.client.newBuilder()
        .rateLimit(baseUrl.toHttpUrl(), 2)
        .build()

    override val useNewChapterEndpoint = true
    override val useLoadMoreRequest = LoadMoreStrategy.Always
}
