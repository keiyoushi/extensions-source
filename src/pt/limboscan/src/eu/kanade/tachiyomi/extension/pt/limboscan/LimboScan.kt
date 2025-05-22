package eu.kanade.tachiyomi.extension.pt.limboscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class LimboScan : Madara(
    "Limbo Scan",
    "https://limboscan.com.br",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()

    override val mangaSubString = "obras"

    override val useNewChapterEndpoint = true
}
