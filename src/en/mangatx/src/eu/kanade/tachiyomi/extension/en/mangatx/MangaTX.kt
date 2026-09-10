package eu.kanade.tachiyomi.extension.en.mangatx

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

@Source
abstract class MangaTX : MangaThemesia() {
    override val mangaUrlDirectory = "/manga-list"

    override val datePattern = "dd-MM-yyyy"

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    override val seriesAuthorSelector = ".imptdt:contains(Author) a"

    override val supportsRelatedMangas = false
}
