package eu.kanade.tachiyomi.extension.en.suryascans

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.network.rateLimit
import kotlin.time.Duration.Companion.seconds

class GenzToons :
    Keyoapp(
        "Genz Toons",
        "https://genztoons.org",
        "en",
    ) {

    override val client = super.client.newBuilder()
        .connectTimeout(90.seconds)
        .writeTimeout(90.seconds)
        .readTimeout(90.seconds)
        .rateLimit(3)
        .build()

    override fun fetchPopularManga(page: Int) = fetchSearchManga(page, "", FilterList())
}
