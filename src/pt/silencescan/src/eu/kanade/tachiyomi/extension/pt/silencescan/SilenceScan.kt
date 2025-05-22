package eu.kanade.tachiyomi.extension.pt.silencescan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class SilenceScan : MangaThemesia(
    "Silence Scan",
    "https://silencescan.com.br",
    "pt-BR",
    dateFormat = SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR")),
) {

    override val versionId: Int = 2

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()

    override val altNamePrefix = "Nome alternativo: "
}
