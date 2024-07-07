package eu.kanade.tachiyomi.extension.es.vermanhwas

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

class VerManhwas : Madara(
    "Ver Manhwas",
    "https://vermanhwa.com",
    "es",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("es")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false

    override fun genresRequest(): Request {
        return GET("$baseUrl/?s=&post_type=wp-manga", headers)
    }
}
