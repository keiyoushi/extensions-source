package eu.kanade.tachiyomi.extension.tr.mangakusu

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class MangaKusu : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("tr"))
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
