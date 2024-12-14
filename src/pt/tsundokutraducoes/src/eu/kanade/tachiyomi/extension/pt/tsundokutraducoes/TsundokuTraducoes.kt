package eu.kanade.tachiyomi.extension.pt.tsundokutraducoes

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class TsundokuTraducoes : MangaThemesia(
    "Tsundoku Traduções",
    "https://tsundoku.com.br",
    "pt-BR",
    dateFormat = SimpleDateFormat("MMMMM d, yyyy", Locale("pt", "BR")),
) {

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    override val altNamePrefix = "Nome alternativo: "

    override fun searchMangaSelector() = ".utao .uta .imgu, .listupd .bs .bsx:not(:has(span.novelabel)), .listo .bs .bsx:not(:has(span.novelabel))"
}
