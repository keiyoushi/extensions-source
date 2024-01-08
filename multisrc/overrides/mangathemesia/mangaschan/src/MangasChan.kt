package eu.kanade.tachiyomi.extension.pt.mangaschan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MangasChan : MangaThemesia(
    "Mangás Chan",
    "https://mangaschan.net",
    "pt-BR",
    dateFormat = SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR")),
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    override val altNamePrefix = "Nomes alternativos: "

    override val seriesArtistSelector = ".tsinfo .imptdt:contains(Artista) > i"
    override val seriesAuthorSelector = ".tsinfo .imptdt:contains(Autor) > i"
    override val seriesTypeSelector = ".tsinfo .imptdt:contains(Tipo) > a"
}
