package eu.kanade.tachiyomi.extension.es.dragontranslationorg

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class DragonTranslationOrg :
    Madara(
        "DragonTranslation.org",
        "https://dragontranslation.org",
        "es",
        SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
    ) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
