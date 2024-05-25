package eu.kanade.tachiyomi.extension.id.siimanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class Siikomik : Madara(
    "Siikomik",
    "https://siikomik.com",
    "id",
    dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.US),
) {
    override val id = 5693774260946188681

    override val mangaSubString = "komik"

    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val useNewChapterEndpoint = false
}
