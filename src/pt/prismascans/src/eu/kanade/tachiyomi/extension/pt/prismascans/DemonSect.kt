package eu.kanade.tachiyomi.extension.pt.prismascans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.OkHttpClient
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class DemonSect : Madara(
    "Demon Sect",
    "https://demonsect.com.br",
    "pt-BR",
    SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR")),
) {

    // Changed their name from Prisma Scans to Demon Sect.
    override val id: Long = 8168108118738519332

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val useNewChapterEndpoint = true

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return client.newCall(popularMangaRequest(page))
            .asObservable() // Site returns http 404 even if the result is successful
            .map { response ->
                popularMangaParse(response)
            }
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return client.newCall(latestUpdatesRequest(page))
            .asObservable() // Site returns http 404 even if the result is successful
            .map { response ->
                latestUpdatesParse(response)
            }
    }
}
