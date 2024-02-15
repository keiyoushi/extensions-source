package eu.kanade.tachiyomi.extension.es.esmi2manga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class EsMi2Manga : Madara(
    "Es.Mi2Manga",
    "https://es.mi2manga.com",
    "es",
    SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()

    override fun popularMangaSelector() = "div.site-content div.page-item-detail:not(:has(a[href*='bilibilicomics.com']))$mangaEntrySelector"
    override fun searchMangaSelector() = "div.site-content div.c-tabs-item__content"
}
