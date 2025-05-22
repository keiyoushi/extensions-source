package eu.kanade.tachiyomi.extension.pt.tsundokutraducoes

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class TsundokuTraducoes : MangaThemesia(
    "Tsundoku Traduções",
    "https://tsundoku.com.br",
    "pt-BR",
    dateFormat = SimpleDateFormat("MMMMM d, yyyy", Locale("pt", "BR")),
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()

    override val altNamePrefix = "Nome alternativo: "

    override fun searchMangaSelector() = ".utao .uta .imgu, .listupd .bs .bsx:not(:has(span.novelabel)), .listo .bs .bsx:not(:has(span.novelabel))"
}
