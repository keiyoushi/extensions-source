package eu.kanade.tachiyomi.extension.pt.minitwoscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MiniTwoScan : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("pt", "BR"))
    override val chapterMode = ChapterMode.AdminAjax

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(1, 2.seconds)
    }
}
