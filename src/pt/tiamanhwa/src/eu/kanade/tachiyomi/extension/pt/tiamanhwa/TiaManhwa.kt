package eu.kanade.tachiyomi.extension.pt.tiamanhwa

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class TiaManhwa : Madara(
    "Tia Manhwa",
    "https://tiamanhwa.com",
    "pt-BR",
    dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override val mangaSubString = "manhwa-em-portugues"

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val useNewChapterEndpoint = true
}
