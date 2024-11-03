package eu.kanade.tachiyomi.extension.es.jobsibe

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class Jobsibe : Madara(
    "Jobsibe",
    "https://lmtos.com",
    "es",
    SimpleDateFormat("dd/MM/yyyy", Locale("es")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val useNewChapterEndpoint = true

    override val filterNonMangaItems = false

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun parseChapterDate(date: String?) =
        super.parseChapterDate("$date/${Calendar.getInstance().get(Calendar.YEAR)}")
}
