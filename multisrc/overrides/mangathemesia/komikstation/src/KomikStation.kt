package eu.kanade.tachiyomi.extension.id.komikstation

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.OkHttpClient

class KomikStation : MangaThemesia("Komik Station", "https://komikstation.co", "id") {
    // Formerly "Komik Station (WP Manga Stream)"
    override val id = 6148605743576635261

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(4)
        .build()

    override val seriesTitleSelector = ".ts-breadcrumb li[itemprop]:last-child span"
    override val seriesAuthorSelector = ".infox .fmed:has(.fa-pen-fancy) span"
    override val seriesArtistSelector = ".infox .fmed:has(.fa-paintbrush) span"
    override val seriesTypeSelector = ".tsinfo .imptdt:has(a[href*=\"type\"]) a"
    override val seriesStatusSelector = ".tsinfo .imptdt:first-child i"

    override fun String?.parseStatus(): Int = when {
        this == null -> SManga.UNKNOWN
        listOf("ongoing", "berjalan").any { this.contains(it, ignoreCase = true) } -> SManga.ONGOING
        this.contains("hiatus", ignoreCase = true) -> SManga.ON_HIATUS
        this.contains("tamat", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override val projectPageString = "/project-list"

    override val hasProjectPage = true
}
