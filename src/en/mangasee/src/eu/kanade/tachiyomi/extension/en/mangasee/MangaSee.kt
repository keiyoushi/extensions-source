package eu.kanade.tachiyomi.extension.en.mangasee

import eu.kanade.tachiyomi.multisrc.nepnep.NepNep
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.OkHttpClient
import rx.Observable
import java.util.concurrent.TimeUnit

class MangaSee : NepNep("MangaSee", "https://mangasee123.com", "en") {

    override val id: Long = 9

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(1, 2)
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("id:")) {
            val id = query.substringAfter("id:")
            return client.newCall(GET("$baseUrl/manga/$id"))
                .asObservableSuccess()
                .map { response ->
                    val manga = mangaDetailsParse(response)
                    manga.url = "/manga/$id"
                    MangasPage(listOf(manga), false)
                }
        }

        return super.fetchSearchManga(page, query, filters)
    }
}
