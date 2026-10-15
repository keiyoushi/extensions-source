package eu.kanade.tachiyomi.extension.pt.amuy

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Amuy : MadaraNoAjax() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR"))

    override fun OkHttpClient.Builder.configureClient() = rateLimit(1, 2.seconds)

    override val chapterMode = ChapterMode.MangaAjax
}
