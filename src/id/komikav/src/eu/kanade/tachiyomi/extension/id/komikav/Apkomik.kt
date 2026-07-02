package eu.kanade.tachiyomi.extension.id.komikav

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Apkomik : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.forLanguageTag("id"))

    override val client = super.client.newBuilder()
        .rateLimit(4)
        .build()

    override val hasProjectPage = true
}
