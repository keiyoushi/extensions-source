package eu.kanade.tachiyomi.extension.pt.tankouhentai

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.MadaraBase.ChapterMode
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class TankouHentai : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale("pt", "BR"))

    override fun OkHttpClient.Builder.configureClient() = rateLimit(1, 2.seconds) { !it.encodedPath.startsWith("/wp-content/uploads/") }
}
