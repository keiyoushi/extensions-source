package eu.kanade.tachiyomi.extension.id.kiryuu

import eu.kanade.tachiyomi.multisrc.natsuid.NatsuId
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.OkHttpClient
import okhttp3.Request

class Kiryuu : NatsuId(
    "Kiryuu",
    "id",
    "https://kiryuu03.com",
) {
    // Formerly "Kiryuu (WP Manga Stream)"
    override val id = 3639673976007021338

    override fun OkHttpClient.Builder.customizeClient() = rateLimit(4)

    override fun chapterListRequest(manga: SManga): Request {
        val url = super.chapterListRequest(manga).url.newBuilder()
            .setQueryParameter("page", "1")
            .build()

        return GET(url, headers)
    }
}
