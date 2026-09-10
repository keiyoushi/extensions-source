package eu.kanade.tachiyomi.extension.pt.tsundokutraducoes

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Source
abstract class TsundokuTraducoes : MangaThemesia() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(1, 2.seconds)

    override val altNamePrefix = "Nome alternativo: "

    override fun searchMangaSelector() = ".utao .uta .imgu, .listupd .bs .bsx:not(:has(span.novelabel)), .listo .bs .bsx:not(:has(span.novelabel))"
}
