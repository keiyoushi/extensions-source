package eu.kanade.tachiyomi.extension.pt.origamiorpheans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class OrigamiOrpheans : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR"))

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()

    override val altNamePrefix = "Nomes alternativos: "
}
