package eu.kanade.tachiyomi.extension.pt.prismascans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class SeitaCelestial : MangaThemesia(
    "Seita Celestial",
    "https://seitacelestial.com",
    "pt-BR",
    mangaUrlDirectory = "/comics",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("pt", "BR")),
) {

    // They changed their name from Prisma Scans to Demon Sect and now to Celestial Sect.
    override val id: Long = 8168108118738519332

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(chapterListRequest(manga))
            .asObservable()
            .map { response ->
                if (!response.isSuccessful) {
                    throw Exception(
                        """
                            Obra não encontrada.
                            Realize a migração do título para atualizar.
                        """.trimIndent(),
                    )
                }
                chapterListParse(response)
            }
    }
}
