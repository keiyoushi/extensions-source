package eu.kanade.tachiyomi.extension.en.yaoihub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

class Yaoihub : Madara("Yaoihub", "https://yaoihub.com", "en") {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()

    override val useNewChapterEndpoint = true
}
