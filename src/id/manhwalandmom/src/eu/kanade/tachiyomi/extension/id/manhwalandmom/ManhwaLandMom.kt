package eu.kanade.tachiyomi.extension.id.manhwalandmom

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class ManhwaLandMom : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("id"))

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(4)
        .build()
}
