package eu.kanade.tachiyomi.extension.pt.ghostscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class GhostScan : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale("pt", "BR"))
    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(1, 2.seconds)
    }

    override val chapterMode = ChapterMode.MangaAjax
}
