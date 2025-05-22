package eu.kanade.tachiyomi.extension.pt.xxxyaoi

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class XXXYaoi : Madara(
    "XXX Yaoi",
    "https://3xyaoi.com",
    "pt-BR",
    SimpleDateFormat("MMMM dd, yyyy", Locale("pt", "BR")),
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()

    override val useNewChapterEndpoint = true

    override val mangaSubString = "bl"

    override val mangaDetailsSelectorAuthor = mangaDetailsSelectorArtist

    override val mangaDetailsSelectorStatus = "div.post-content_item:contains(Status) > div.summary-content"
}
