package eu.kanade.tachiyomi.extension.pt.leitordemanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class LeitorDeManga : Madara(
    "Leitor de Mangá",
    "https://leitordemanga.com",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
) {
    override val mangaSubString = "ler-manga"

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()
}
