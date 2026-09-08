package eu.kanade.tachiyomi.extension.pt.yuriverso

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class YuriOnAir : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))
    override val chapterMode = ChapterMode.MangaAjax

    override fun OkHttpClient.Builder.configureClient() = rateLimit(1, 2.seconds) { !it.encodedPath.startsWith("/wp-content/uploads/") }
}
