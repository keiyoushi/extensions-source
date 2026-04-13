package eu.kanade.tachiyomi.extension.pt.xxxyaoi

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class XXXYaoi :
    Madara(
        "XXX Yaoi",
        "https://3xyaoi.com",
        "pt-BR",
        SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
    ) {

    override fun headersBuilder() = super.headersBuilder()
        .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .set("Upgrade-Insecure-Requests", "1")
        .set("Sec-GPC", "1")
        .set("Sec-Fetch-User", "?1")
        .set("Sec-Fetch-Site", "none")
        .set("Sec-Fetch-Mode", "navigate")
        .set("Sec-Fetch-Dest", "document")
        .set("Priority", "u=0, i")
        .set("Pragma", "no-cache")

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(3, 1, TimeUnit.SECONDS)
        .build()

    override val useNewChapterEndpoint = true

    override val mangaSubString = "bl"

    override val mangaDetailsSelectorAuthor = mangaDetailsSelectorArtist

    override val mangaDetailsSelectorStatus = "div.post-content_item:contains(Status) > div.summary-content"
}
