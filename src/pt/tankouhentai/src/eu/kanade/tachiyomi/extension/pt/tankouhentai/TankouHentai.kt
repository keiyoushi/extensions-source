package eu.kanade.tachiyomi.extension.pt.tankouhentai

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class TankouHentai : Madara(
    "Tankou Hentai",
    "https://tankouhentai.com",
    "pt-BR",
    SimpleDateFormat("dd 'de' MMMMM 'de' YYYY", Locale("pt", "BR")),
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()

    override val useNewChapterEndpoint = true
}
