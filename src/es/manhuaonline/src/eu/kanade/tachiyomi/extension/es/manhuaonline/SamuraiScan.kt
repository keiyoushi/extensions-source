package eu.kanade.tachiyomi.extension.es.manhuaonline

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class SamuraiScan : Madara(
    "SamuraiScan",
    "https://latan.visorsmr.com",
    "es",
    SimpleDateFormat("dd MMMM, yyyy", Locale("es")),
) {
    override val id = 5713083996691468192

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override val mangaSubString = "rd"

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val mangaDetailsSelectorDescription = "div.summary_content div.manga-summary"
}
