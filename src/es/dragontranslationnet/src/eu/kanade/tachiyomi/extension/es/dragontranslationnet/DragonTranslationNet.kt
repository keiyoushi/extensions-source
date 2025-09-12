package eu.kanade.tachiyomi.extension.es.dragontranslationnet

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class DragonTranslationNet : Madara(
    "DragonTranslation.net",
    "https://dragontranslation.org",
    "es",
    SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("es")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
