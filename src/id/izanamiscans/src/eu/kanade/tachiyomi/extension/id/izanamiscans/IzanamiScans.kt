package eu.kanade.tachiyomi.extension.id.izanamiscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class IzanamiScans : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id"))
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val seriesAuthorSelector = ".fmed b:contains(Penulis) + span"
}
