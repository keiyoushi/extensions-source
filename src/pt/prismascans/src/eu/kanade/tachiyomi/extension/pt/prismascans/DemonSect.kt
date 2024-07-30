package eu.kanade.tachiyomi.extension.pt.prismascans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.OkHttpClient
import okio.IOException
import rx.Observable
import java.util.concurrent.TimeUnit

class DemonSect : MangaThemesia(
    "Demon Sect",
    "https://dsectcomics.org",
    "pt-BR",
    "/comics",
) {

    // Changed their name from Prisma Scans to Demon Sect.
    override val id: Long = 8168108118738519332

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
            .asObservable()
            .map { response ->
                if (!response.isSuccessful) {
                    throw IOException(
                        """
                            Obra não encontrada. Realize a migração do título para atualizar.
                        """.trimIndent(),
                    )
                }
                mangaDetailsParse(response).apply { initialized = true }
            }
    }
}
