package eu.kanade.tachiyomi.extension.en.suryascans

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.network.rateLimit
import java.util.concurrent.TimeUnit

class GenzToons :
    Keyoapp(
        "Genz Toons",
        "https://genztoons.org",
        "en",
    ) {

    override val client = super.client.newBuilder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .rateLimit(3)
        .build()

    override fun fetchPopularManga(page: Int) = fetchSearchManga(page, "", FilterList())
}
