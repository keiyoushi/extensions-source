package eu.kanade.tachiyomi.extension.id.komikindoco

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class KomikindoCo : MangaThemesia("KomikIndo.co", "https://komiksin.id", "id", dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale("id"))) {
    // Formerly "Komikindo.co"
    override val id = 734619124437406170

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(4)
        .build()

    override val hasProjectPage = true

    override val seriesDetailsSelector = ".seriestucon"
}
