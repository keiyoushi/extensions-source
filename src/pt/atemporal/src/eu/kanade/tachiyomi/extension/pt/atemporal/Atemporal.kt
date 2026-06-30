package eu.kanade.tachiyomi.extension.pt.atemporal

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Atemporal : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale("pt", "BR"))
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
