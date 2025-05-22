package eu.kanade.tachiyomi.extension.pt.origamiorpheans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class OrigamiOrpheans : MangaThemesia(
    "Origami Orpheans",
    "https://origami-orpheans.com",
    "pt-BR",
    dateFormat = SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR")),
) {

    // Scanlator migrated from Madara to WpMangaReader.
    override val versionId = 2

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()

    override val altNamePrefix = "Nomes alternativos: "
}
