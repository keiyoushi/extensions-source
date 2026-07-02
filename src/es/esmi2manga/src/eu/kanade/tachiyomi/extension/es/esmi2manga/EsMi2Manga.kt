package eu.kanade.tachiyomi.extension.es.esmi2manga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class EsMi2Manga : Madara() {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es"))
    override val client = super.client.newBuilder()
        .rateLimit(2, 1.seconds)
        .build()

    override fun popularMangaSelector() = "div.site-content div.page-item-detail:not(:has(a[href*='bilibilicomics.com']))$mangaEntrySelector"
    override fun searchMangaSelector() = "div.site-content div.c-tabs-item__content"
}
