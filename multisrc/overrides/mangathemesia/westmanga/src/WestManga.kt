package eu.kanade.tachiyomi.extension.id.westmanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient

class WestManga : MangaThemesia("West Manga", "https://westmanga.org", "id") {
    // Formerly "West Manga (WP Manga Stream)"
    override val id = 8883916630998758688

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(4)
        .build()

    override val seriesDetailsSelector = ".seriestucontent"
    override val seriesTypeSelector = ".infotable tr:contains(Type) td:last-child"

    override val hasProjectPage = true
}
