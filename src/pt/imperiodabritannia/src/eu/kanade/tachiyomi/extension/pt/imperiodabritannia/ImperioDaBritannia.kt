package eu.kanade.tachiyomi.extension.pt.imperiodabritannia

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class ImperioDaBritannia : Madara(
    "Sagrado Império da Britannia",
    "https://imperiodabritannia.com",
    "pt-BR",
    SimpleDateFormat("dd 'de' MMMMM 'de' yyyy", Locale("pt", "BR")),
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val mangaDetailsSelectorTag = ""

    override val mangaDetailsSelectorAuthor =
        ".summary-heading:has(h5:contains(Autor)) + div > ${super.mangaDetailsSelectorAuthor}"
    override val mangaDetailsSelectorArtist =
        ".summary-heading:has(h5:contains(Artista)) + div > ${super.mangaDetailsSelectorArtist}"
    override val mangaDetailsSelectorStatus =
        ".summary-heading:has(h5:contains(Status)) + ${super.mangaDetailsSelectorStatus}"
}
