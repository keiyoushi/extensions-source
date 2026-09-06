package eu.kanade.tachiyomi.extension.en.skymanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

@Source
abstract class SkyManga : MangaThemesia() {
    override val mangaUrlDirectory = "/manga-list"
    override val datePattern = "dd-MM-yyyy"
    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)
}
