package eu.kanade.tachiyomi.extension.pt.imperioscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class ImperioScans : Madara(
    "Neroxus",
    "https://neroxus.com.br",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
) {
    // Source renamed from Império Scans to Neroxus
    override val id = 1369117541868504584

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()
}
