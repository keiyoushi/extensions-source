package eu.kanade.tachiyomi.extension.es.dragontranslationorg

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class DragonTranslationOrg : Madara() {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es"))
    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
