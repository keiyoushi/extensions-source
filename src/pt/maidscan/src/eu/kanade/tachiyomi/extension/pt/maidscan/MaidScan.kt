package eu.kanade.tachiyomi.extension.pt.maidscan

import eu.kanade.tachiyomi.multisrc.greenshit.GreenShit
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MaidScan : GreenShit(
    "Maid Scan",
    "https://novo.empreguetes.site",
    "pt-BR",
    scanId = 3,
) {
    override val targetAudience = TargetAudience.Shoujo

    override val contentOrigin: ContentOrigin = ContentOrigin.Mobile

    override val client: OkHttpClient = super.client.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .rateLimit(2)
        .build()

    override fun popularMangaRequest(page: Int) =
        GET("$apiUrl/obras/ranking?periodo=geral&limite=5&gen_id=$targetAudience", headers)
}
