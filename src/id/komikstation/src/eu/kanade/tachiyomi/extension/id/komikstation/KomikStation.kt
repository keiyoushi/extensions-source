package eu.kanade.tachiyomi.extension.id.komikstation

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient

class KomikStation : MangaThemesia("Komik Station", "https://komikstation.co", "id") {
    // Formerly "Komik Station (WP Manga Stream)"
    override val id = 6148605743576635261

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(4)
        .build()

    override val projectPageString = "/project-list"

    override val hasProjectPage = true
}
