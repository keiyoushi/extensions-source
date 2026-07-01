package eu.kanade.tachiyomi.extension.id.komikindoco

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class KomikindoCo : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale("id"))

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(4)
        .build()

    override val hasProjectPage = true

    override val seriesDetailsSelector = ".seriestucon"
}
