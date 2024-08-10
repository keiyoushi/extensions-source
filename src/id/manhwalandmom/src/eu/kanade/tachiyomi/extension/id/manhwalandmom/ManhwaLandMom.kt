package eu.kanade.tachiyomi.extension.id.manhwalandmom

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class ManhwaLandMom : MangaThemesia(
    "ManhwaLand.mom",
    "https://www.manhwaland.ink",
    "id",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("id")),
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(4)
        .build()
}
