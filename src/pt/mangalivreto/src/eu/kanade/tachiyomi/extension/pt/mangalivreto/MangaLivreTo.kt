package eu.kanade.tachiyomi.extension.pt.mangalivreto

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaLivreTo : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale("pt"))

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2)
    }

    override fun chapterListSelector() = ".listing-chapters-wrap .chapter-box"

    override val chapterDateSelector = ".chapter-date"
}
