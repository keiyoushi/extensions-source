package eu.kanade.tachiyomi.extension.vi.gantzvn

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class GantzVN : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    override val mangaSubString = "truyen"

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(3)
    }
}
