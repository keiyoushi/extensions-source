package eu.kanade.tachiyomi.extension.vi.gantzvn

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class GantzVN : Madara() {
    override val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)
    override val mangaSubString = "truyen"

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(3)
        .build()
}
