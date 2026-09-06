package eu.kanade.tachiyomi.extension.id.komikstation

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

@Source
abstract class KomikStation : MangaThemesia() {
    override fun OkHttpClient.Builder.configureClient() = rateLimit(4)

    override val projectPageString = "/project-list"

    override val hasProjectPage = true
}
