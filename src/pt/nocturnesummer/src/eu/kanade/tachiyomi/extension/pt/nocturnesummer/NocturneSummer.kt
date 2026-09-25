package eu.kanade.tachiyomi.extension.pt.nocturnesummer

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class NocturneSummer : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR"))

    override fun OkHttpClient.Builder.configureClient() = rateLimit(1, 2.seconds)

    override val mangaDetailsSelectorStatus = "div.post-content_item:contains(Estado) > div.summary-content"
    override val chapterMode = ChapterMode.MangaAjax
}
