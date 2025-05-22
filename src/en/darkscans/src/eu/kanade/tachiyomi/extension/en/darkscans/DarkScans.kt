package eu.kanade.tachiyomi.extension.en.darkscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

class DarkScans : Madara("Dark Scans", "https://darkscans.net", "en") {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(20, 4.seconds)
        .build()

    override val mangaSubString = "mangas"

    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true

    override val filterNonMangaItems = false
}
